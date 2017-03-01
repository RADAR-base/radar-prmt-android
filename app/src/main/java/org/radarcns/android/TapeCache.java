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
    private final ReentrantLock tapeLock;
    private final ReentrantLock tapeThreadLock;
    private final AtomicBoolean readerIsActive;
    private final Condition tapeIsFlushed;
    private final Condition tapeWritingShouldPause;
    private long lastOffsetSent;
    private long timeWindowMillis;
    private Future<?> addMeasurementFuture;

    public TapeCache(Context context, AvroTopic<K, V> topic, long timeWindowMillis) throws IOException {
        this.topic = topic;
        this.timeWindowMillis = timeWindowMillis;
        File outputFile = new File(context.getFilesDir(), topic.getName() + ".tape");
        QueueFile queueFile = new QueueFile(outputFile);
        this.executor = Executors.newSingleThreadScheduledExecutor(new AndroidThreadFactory(
                "TapeCache-" + TapeCache.this.topic.getName(),
                Process.THREAD_PRIORITY_BACKGROUND));
        this.measurementsToAdd = new ArrayList<>();


        tapeLock = new ReentrantLock();
        tapeWritingShouldPause = tapeLock.newCondition();
        readerIsActive = new AtomicBoolean(false);
        tapeThreadLock = new ReentrantLock();
        tapeIsFlushed = tapeThreadLock.newCondition();

        tapeLock.lock();
        this.queue = new BackedObjectQueue<>(queueFile, new TapeAvroConverter<>(topic));

        long firstInQueue;
        if (queue.isEmpty()) {
            firstInQueue = 0L;
        } else {
            firstInQueue = queue.peek().offset;
        }
        lastOffsetSent = firstInQueue - 1L;
        nextOffset = new AtomicLong(firstInQueue + queue.size());
        tapeLock.unlock();
    }

    @Override
    public List<Record<K, V>> unsentRecords(int limit) throws IOException {
        List<Record<K, V>> records = listPool.get(Collections.<Record<K,V>>emptyList());

        readerIsActive.set(true);
        tapeLock.lock();
        readerIsActive.set(false);
        tapeWritingShouldPause.signalAll();
        try {
            records.addAll(queue.peek(limit));
        } finally {
            tapeLock.unlock();
        }

        return records;
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
        tapeLock.lock();
        try {
            return queue.size();
        } finally {
            tapeLock.unlock();
        }
    }

    @Override
    public void setTimeWindow(long timeWindowMillis) {
        tapeThreadLock.lock();
        this.timeWindowMillis = timeWindowMillis;
        tapeThreadLock.unlock();
    }

    @Override
    public int markSent(long offset) throws IOException {
        readerIsActive.set(true);
        tapeLock.lock();
        readerIsActive.set(false);
        tapeWritingShouldPause.signalAll();
        try {
            logger.info("marking offset {} sent for topic {}, with last offset in data being {}",
                    offset, topic, lastOffsetSent);
            if (offset <= lastOffsetSent) {
                return 0;
            }
            lastOffsetSent = offset;

            if (queue.isEmpty()) {
                return 0;
            } else {
                int toRemove = (int)(offset - queue.peek().offset + 1);
                logger.info("Removing data from topic {} at offset {} onwards ({} records)",
                        topic, offset, toRemove);
                queue.remove(toRemove);
                logger.info("Removed data from topic {} at offset {} onwards ({} records)",
                        topic, offset, toRemove);
                return toRemove;
            }
        } finally {
            tapeLock.unlock();
        }
    }

    @Override
    public void addMeasurement(final K key, final V value) {
        tapeThreadLock.lock();
        measurementsToAdd.add(new Record<>(nextOffset.getAndIncrement(), key, value));

        if (addMeasurementFuture == null) {
            addMeasurementFuture = executor.schedule(new Runnable() {
                @Override
                public void run() {
                    List<Record<K, V>> localList;

                    tapeThreadLock.lock();
                    addMeasurementFuture = null;
                    localList = listPool.get(measurementsToAdd);
                    measurementsToAdd.clear();
                    tapeThreadLock.unlock();

                    tapeLock.lock();
                    try {
                        while (readerIsActive.get()) {
                            tapeWritingShouldPause.await();
                        }
                        logger.info("Writing {} records to file in topic {}", localList.size(), topic);
                        for (Record<K, V> record : localList) {
                            queue.add(record);
                        }
                    } catch (IOException ex) {
                        logger.error("Failed to add record", ex);
                        throw new RuntimeException(ex);
                    } catch (InterruptedException ex) {
                        logger.warn("Tape writing was interrupted");
                    } finally {
                        tapeLock.unlock();
                    }

                    listPool.add(localList);

                    tapeThreadLock.lock();
                    tapeIsFlushed.signalAll();
                    tapeThreadLock.unlock();
                }
            }, timeWindowMillis, TimeUnit.MILLISECONDS);
        }
        tapeThreadLock.unlock();
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
    public void flush() {
        tapeThreadLock.lock();
        try {
            while (addMeasurementFuture != null) {
                tapeIsFlushed.await();
            }
        } catch (InterruptedException e) {
            logger.warn("Did not wait for adding measurements to complete.");
        } finally {
            tapeThreadLock.unlock();
        }
    }
}
