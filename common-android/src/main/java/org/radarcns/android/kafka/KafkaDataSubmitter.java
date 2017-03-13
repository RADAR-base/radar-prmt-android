package org.radarcns.android.kafka;

import android.support.annotation.NonNull;

import org.radarcns.android.data.DataCache;
import org.radarcns.android.data.DataHandler;
import org.radarcns.data.Record;
import org.radarcns.producer.KafkaSender;
import org.radarcns.producer.KafkaTopicSender;
import org.radarcns.topic.AvroTopic;
import org.radarcns.util.ListPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Separate thread to read from the database and send it to the Kafka server. It cleans the
 * database.
 *
 * It uses a set of timers to addMeasurement data and clean the databases.
 */
public class KafkaDataSubmitter<K, V> implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(KafkaDataSubmitter.class);

    private final DataHandler<K, V> dataHandler;
    private final KafkaSender<K, V> sender;
    private final ScheduledExecutorService executor;
    private final ConcurrentMap<AvroTopic<K, V>, Queue<Record<K, V>>> trySendCache;
    private final Map<AvroTopic<K, V>, ScheduledFuture<?>> trySendFuture;
    private final Map<AvroTopic<K, V>, KafkaTopicSender<K, V>> topicSenders;
    private final KafkaConnectionChecker connection;
    private static final ListPool listPool = new ListPool(1);
    private final AtomicInteger sendLimit;

    private boolean lastUploadFailed = false;
    private ScheduledFuture<?> cleanFuture;
    private ScheduledFuture<?> uploadFuture;

    public KafkaDataSubmitter(@NonNull DataHandler<K, V> dataHandler, @NonNull KafkaSender<K, V> sender, ThreadFactory threadFactory, int sendLimit, long uploadRate, long cleanRate) {
        this.dataHandler = dataHandler;
        this.sender = sender;
        trySendCache = new ConcurrentHashMap<>();
        trySendFuture = new HashMap<>();
        topicSenders = new HashMap<>();
        this.sendLimit = new AtomicInteger(sendLimit);

        executor = Executors.newSingleThreadScheduledExecutor(threadFactory);
        logger.info("Started data submission executor");

        connection = new KafkaConnectionChecker(sender, executor, dataHandler);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (KafkaDataSubmitter.this.sender.isConnected()) {
                    KafkaDataSubmitter.this.dataHandler.updateServerStatus(ServerStatusListener.Status.CONNECTED);
                    connection.didConnect();
                } else {
                    KafkaDataSubmitter.this.dataHandler.updateServerStatus(ServerStatusListener.Status.DISCONNECTED);
                    connection.didDisconnect(null);
                }
            }
        });

        synchronized (this) {
            uploadFuture = null;
            setUploadRate(uploadRate);
            cleanFuture = null;
            setCleanRate(cleanRate);
        }
        logger.info("Remote Config: Upload rate is '{}' sec per upload, clean is {} sec per upload",
                uploadRate, cleanRate);
    }

    public final synchronized void setUploadRate(long period) {
        if (uploadFuture != null) {
            uploadFuture.cancel(false);
        }
        // Get upload frequency from system property
        uploadFuture = executor.scheduleAtFixedRate(new Runnable() {
            Set<AvroTopic<K, ? extends V>> topicsToSend = Collections.emptySet();

            @Override
            public void run() {
                if (connection.isConnected()) {
                    if (topicsToSend.isEmpty()) {
                        topicsToSend = new HashSet<>(KafkaDataSubmitter.this.dataHandler.getCaches().keySet());
                    }
                    uploadCaches(topicsToSend);
                    // still more data to send, do that immediately
                    if (!topicsToSend.isEmpty()) {
                        executor.execute(this);
                    }
                } else {
                    topicsToSend.clear();
                }
            }
        }, period, period, TimeUnit.SECONDS);
    }

    public final synchronized void setCleanRate(long period) {
        if (cleanFuture != null) {
            cleanFuture.cancel(false);
        }

        // Remove old data from tables infrequently
        cleanFuture = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                KafkaDataSubmitter.this.dataHandler.clean();
            }
        }, 0L, period, TimeUnit.SECONDS);
    }

    public void setSendLimit(int limit) {
        sendLimit.set(limit);
    }

    /**
     * Close the submitter eventually.
     *
     * Call {@link #join(long)} to wait for this to finish.
     */
    public void close() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Map<AvroTopic<K, ? extends V>, ? extends DataCache<K, ? extends V>> caches = dataHandler.getCaches();
                for (DataCache<K, ? extends V> cache : caches.values()) {
                    try {
                        cache.flush();
                    } catch (IOException ex) {
                        logger.error("Cannot flush cache", ex);
                    }
                }
                if (connection.isConnected()) {
                    uploadCaches(new HashSet<>(caches.keySet()));
                    try {
                        synchronized (trySendFuture) {
                            for (ScheduledFuture<?> future : trySendFuture.values()) {
                                future.cancel(true);
                            }
                            trySendFuture.clear();

                            for (Map.Entry<AvroTopic<K, V>, Queue<Record<K, V>>> records : trySendCache.entrySet()) {
                                doImmediateSend(records.getKey(), listPool.get(records.getValue()));
                            }
                            trySendCache.clear();
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to addMeasurement latest measurements", e);
                    }
                }

                for (Map.Entry<AvroTopic<K, V>, KafkaTopicSender<K, V>> topicSender : topicSenders.entrySet()) {
                    try {
                        topicSender.getValue().close();
                    } catch (IOException e) {
                        logger.warn("failed to close topicSender for topic {}", topicSender.getKey().getName(), e);
                    }
                }
                topicSenders.clear();

                try {
                    sender.close();
                } catch (IOException e1) {
                    logger.warn("failed to addMeasurement latest batches", e1);
                }
            }
        });
        executor.shutdown();
    }

    /** Get a sender for a topic. Per topic, only ONE thread may use this. */
    private KafkaTopicSender<K, V> sender(AvroTopic<K, V> topic) throws IOException {
        KafkaTopicSender<K, V> topicSender = topicSenders.get(topic);
        if (topicSender == null) {
            topicSender = sender.sender(topic);
            topicSenders.put(topic, topicSender);
        }
        return topicSender;
    }

    /**
     * Wait for the executor to finish.
     * @param millis milliseconds to wait.
     * @throws InterruptedException
     */
    public void join(long millis) throws InterruptedException {
        executor.awaitTermination(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Check the connection status eventually.
     */
    public void checkConnection() {
        connection.check();
    }

    /**
     * Upload a limited amount of data stored in the database which is not yet sent.
     */
    private void uploadCaches(Set<AvroTopic<K, ? extends V>> toSend) {
        boolean uploadingNotified = false;
        int currentSendLimit = sendLimit.get();
        try {
            for (Map.Entry<AvroTopic<K, ? extends V>, ? extends DataCache<K, ? extends V>> entry : dataHandler.getCaches().entrySet()) {
                if (!toSend.contains(entry.getKey())) {
                    continue;
                }
                @SuppressWarnings("unchecked") // we can upload any record
                int sent = uploadCache((AvroTopic<K, V>)entry.getKey(), (DataCache<K, V>)entry.getValue(), currentSendLimit, uploadingNotified);
                if (sent < currentSendLimit) {
                    toSend.remove(entry.getKey());
                }
                if (!uploadingNotified && sent > 0) {
                    uploadingNotified = true;
                }
            }
            if (uploadingNotified) {
                dataHandler.updateServerStatus(ServerStatusListener.Status.CONNECTED);
                connection.didConnect();
            }
        } catch (IOException ex) {
            if (!lastUploadFailed) {
                connection.didDisconnect(ex);
            }
        }
    }

    /**
     * Upload some data from a single table.
     * @return number of records sent.
     */
    private int uploadCache(AvroTopic<K, V> topic, DataCache<K, V> cache, int limit,
                            boolean uploadingNotified) throws IOException {
        List<Record<K, V>> measurements = cache.unsentRecords(limit);
        int numberOfRecords = measurements.size();

        if (numberOfRecords > 0) {
            KafkaTopicSender<K, V> cacheSender = sender(topic);

            if (! uploadingNotified ) {
                dataHandler.updateServerStatus(ServerStatusListener.Status.UPLOADING);
            }

            lastUploadFailed = false;
            try {
                cacheSender.send(measurements);
                cacheSender.flush();
            } catch (IOException ioe) {
                lastUploadFailed = true;
                dataHandler.updateServerStatus(ServerStatusListener.Status.UPLOADING_FAILED);
                dataHandler.updateRecordsSent(topic.getName(), -1);
                logger.warn("UPF cacheSender.send failed. {} n_records = {}", topic, numberOfRecords);
                throw ioe;
            }

            long lastOffsetSent = cacheSender.getLastSentOffset();
            cache.markSent(lastOffsetSent);

            dataHandler.updateRecordsSent(topic.getName(), numberOfRecords);

            logger.debug("uploaded {} {} records", numberOfRecords, topic.getName());

            long lastOffsetPut = measurements.get(numberOfRecords - 1).offset;
            if (lastOffsetSent == lastOffsetPut) {
                cache.returnList(measurements);
            }
        } else {
            cache.returnList(measurements);
        }

        return numberOfRecords;
    }

    /**
     * Try to addMeasurement a message, without putting it in any permanent storage. Any failure may cause
     * messages to be lost. If the sender is disconnected, messages are immediately discarded.
     * @return whether the message was queued for sending.
     */
    public <W extends V> boolean trySend(final AvroTopic<K, W> topic, final long offset,
                                         final K deviceId, final W record) {
        if (!connection.isConnected()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        final AvroTopic<K, V> castTopic = (AvroTopic<K, V>)topic;
        Queue<Record<K, V>> records = trySendCache.get(castTopic);
        if (records == null) {
            records = new ConcurrentLinkedQueue<>();
            //noinspection unchecked
            trySendCache.put((AvroTopic<K, V>)topic, records);
        }
        records.add(new Record<>(offset, deviceId, (V)record));

        synchronized (trySendFuture) {
            if (!trySendFuture.containsKey(topic)) {
                trySendFuture.put(castTopic, executor.schedule(new Runnable() {
                    @Override
                    public void run() {
                        if (!connection.isConnected()) {
                            return;
                        }

                        List<Record<K, V>> localRecords;

                        synchronized (trySendFuture) {
                            Queue<Record<K, V>> queue = trySendCache.get(topic);
                            trySendFuture.remove(topic);
                            localRecords = listPool.get(queue);
                        }

                        try {
                            doImmediateSend(castTopic, localRecords);
                            connection.didConnect();
                        } catch (IOException e) {
                            dataHandler.updateRecordsSent(topic.getName(), -1);
                            connection.didDisconnect(e);
                        }
                    }
                }, 5L, TimeUnit.SECONDS));
            }
        }
        return true;
    }

    /** Immediately send given records, without any error recovery. */
    private void doImmediateSend(AvroTopic<K, V> topic, List<Record<K, V>> records)
            throws IOException {
        KafkaTopicSender<K, V> localSender = sender(topic);
        localSender.send(records);
        dataHandler.updateRecordsSent(topic.getName(), records.size());
        long lastOffset = records.get(records.size() - 1).offset;
        if (localSender.getLastSentOffset() == lastOffset) {
            listPool.add(records);
        }
    }
}
