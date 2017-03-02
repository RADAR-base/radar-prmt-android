package org.radarcns.android;

import android.content.Context;
import android.os.Parcel;
import android.os.Process;
import android.util.Pair;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.data.AvroEncoder;
import org.radarcns.data.DataCache;
import org.radarcns.data.Record;
import org.radarcns.data.SpecificRecordEncoder;
import org.radarcns.data.TapeAvroConverter;
import org.radarcns.topic.AvroTopic;
import org.radarcns.util.BackedObjectQueue;
import org.radarcns.util.ListPool;
import org.radarcns.util.QueueFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TapeCache<K extends SpecificRecord, V extends SpecificRecord> implements DataCache<K, V> {
    private static final Logger logger = LoggerFactory.getLogger(TapeCache.class);
    private static final ListPool listPool = new ListPool(10);

    private final AvroTopic<K, V> topic;
    private final BackedObjectQueue<Record<K, V>> queue;
    private final ScheduledExecutorService executor;
    private final AtomicLong nextOffset;
    private final List<Record<K, V>> measurementsToAdd;
    private long lastOffsetSent;
    private long timeWindowMillis;
    private Future<?> addMeasurementFuture;
    private boolean measurementsAreFlushed;

    public TapeCache(Context context, AvroTopic<K, V> topic, long timeWindowMillis) throws IOException {
        this.topic = topic;
        this.timeWindowMillis = timeWindowMillis;
        File outputFile = new File(context.getFilesDir(), topic.getName() + ".tape");
        QueueFile queueFile;
        try {
            queueFile = new QueueFile(outputFile);
        } catch (IOException ex) {
            logger.error("TapeCache " + outputFile + " was corrupted. Removing old cache.");
            if (outputFile.delete()) {
                queueFile = new QueueFile(outputFile);
            } else {
                throw ex;
            }
        }
        this.executor = Executors.newSingleThreadScheduledExecutor(new AndroidThreadFactory(
                "TapeCache-" + TapeCache.this.topic.getName(),
                Process.THREAD_PRIORITY_BACKGROUND));
        this.measurementsToAdd = new ArrayList<>();


        this.queue = new BackedObjectQueue<>(queueFile, new TapeAvroConverter<>(topic));

        long firstInQueue;
        if (queue.isEmpty()) {
            firstInQueue = 0L;
        } else {
            firstInQueue = queue.peek().offset;
        }
        lastOffsetSent = firstInQueue - 1L;
        nextOffset = new AtomicLong(firstInQueue + queue.size());
        measurementsAreFlushed = false;
    }

    @Override
    public List<Record<K, V>> unsentRecords(final int limit) throws IOException {
        try {
            return listPool.get(executor.submit(new Callable<List<Record<K, V>>>() {
                @Override
                public List<Record<K, V>> call() throws Exception {
                    return queue.peek(limit);
                }
            }).get());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return listPool.get(Collections.<Record<K,V>>emptyList());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new IOException("Unknown error occurred", ex);
            }
        }
    }

    @Override
    public List<Record<K, V>> getRecords(int limit) throws IOException {
        return unsentRecords(limit);
    }

    @Override
    public Pair<Long, Long> numberOfRecords() {
        return new Pair<>((long)queueSize(), 0L);
    }

    private int queueSize() {
        try {
            return executor.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    return queue.size();
                }
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            return -1;
        }
    }

    @Override
    public synchronized void setTimeWindow(long timeWindowMillis) {
        this.timeWindowMillis = timeWindowMillis;
    }

    @Override
    public void markSent(final long offset) throws IOException {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                logger.debug("marking offset {} sent for topic {}, with last offset in data being {}",
                        offset, topic, lastOffsetSent);
                if (offset <= lastOffsetSent) {
                    return;
                }
                lastOffsetSent = offset;

                if (!queue.isEmpty()) {
                    try {
                    int toRemove = (int) (offset - queue.peek().offset + 1);
                    logger.info("Removing data from topic {} at offset {} onwards ({} records)",
                            topic, offset, toRemove);
                    queue.remove(toRemove);
                    logger.info("Removed data from topic {} at offset {} onwards ({} records)",
                            topic, offset, toRemove);
                    } catch (IOException ex) {
                        logger.error("Failed to remove data from QueueFile", ex);
                    }
                }
            }
        });
    }

    @Override
    public synchronized void addMeasurement(final K key, final V value) {
        measurementsAreFlushed = false;
        measurementsToAdd.add(new Record<>(nextOffset.getAndIncrement(), key, value));

        if (addMeasurementFuture == null) {
            addMeasurementFuture = executor.schedule(new Runnable() {
                @Override
                public void run() {
                    List<Record<K, V>> localList;

                    synchronized (TapeCache.this) {
                        addMeasurementFuture = null;
                        localList = listPool.get(measurementsToAdd);
                        measurementsToAdd.clear();
                    }

                    try {
                        logger.info("Writing {} records to file in topic {}", localList.size(), topic);
                        queue.addAll(localList);
                    } catch (IOException ex) {
                        logger.error("Failed to add record", ex);
                        throw new RuntimeException(ex);
                    }

                    listPool.add(localList);
                    synchronized (TapeCache.this) {
                        if (measurementsToAdd.isEmpty()) {
                            measurementsAreFlushed = true;
                            notifyAll();
                        }
                    }
                }
            }, timeWindowMillis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public int removeBeforeTimestamp(long millis) {
        return 0;
    }

    @Override
    public AvroTopic<K, V> getTopic() {
        return topic;
    }

    @Override
    public void writeRecordsToParcel(Parcel dest, int limit) throws IOException {
        List<Record<K, V>> records = getRecords(limit);
        SpecificRecordEncoder specificEncoder = new SpecificRecordEncoder(true);
        AvroEncoder.AvroWriter<K> keyWriter = specificEncoder.writer(topic.getKeySchema(), topic.getKeyClass());
        AvroEncoder.AvroWriter<V> valueWriter = specificEncoder.writer(topic.getValueSchema(), topic.getValueClass());

        dest.writeInt(records.size());
        for (Record<K, V> record : records) {
            dest.writeLong(record.offset);
            dest.writeByteArray(keyWriter.encode(record.key));
            dest.writeByteArray(valueWriter.encode(record.value));
        }
        returnList(records);
    }

    @Override
    public void returnList(List list) {
        listPool.add(list);
    }

    @Override
    public void close() {
        flush();
        listPool.clear();
        executor.shutdown();
    }

    @Override
    public synchronized void flush() {
        try {
            while (!measurementsAreFlushed) {
                wait();
            }
        } catch (InterruptedException e) {
            logger.warn("Did not wait for adding measurements to complete.");
        }
    }
}
