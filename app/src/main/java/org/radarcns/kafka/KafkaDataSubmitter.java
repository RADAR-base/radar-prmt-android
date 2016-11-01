package org.radarcns.kafka;

import org.radarcns.data.DataCache;
import org.radarcns.data.DataHandler;
import org.radarcns.data.Record;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Separate thread to read from the database and send it to the Kafka server. It cleans the
 * database.
 *
 * It uses a set of timers to addMeasurement data and clean the databases.
 */
public class KafkaDataSubmitter<K, V> implements Closeable {
    private final static Logger logger = LoggerFactory.getLogger(KafkaDataSubmitter.class);
    private final static int SEND_LIMIT = 5000;
    private DataHandler<K, V> dataHandler;
    private final KafkaSender<K, V> sender;
    private final ScheduledExecutorService executor;
    private final ConcurrentMap<AvroTopic<K, V>, Queue<Record<K, V>>> trySendCache;
    private final Map<AvroTopic<K, V>, ScheduledFuture<?>> trySendFuture;
    private final Map<AvroTopic<K, V>, KafkaTopicSender<K, V>> topicSenders;
    private final AtomicBoolean isConnected;
    private long lastConnection;

    public KafkaDataSubmitter(DataHandler<K, V> dataHandler, KafkaSender<K, V> sender, ThreadFactory threadFactory) {
        this.dataHandler = dataHandler;
        this.sender = sender;
        trySendCache = new ConcurrentHashMap<>();
        trySendFuture = new HashMap<>();
        topicSenders = new HashMap<>();

        executor = Executors.newSingleThreadScheduledExecutor(threadFactory);

        // Remove old data from tables infrequently
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                KafkaDataSubmitter.this.dataHandler.clean();
            }
        }, 0L, 1L, TimeUnit.HOURS);

        // Upload very frequently.
        executor.scheduleAtFixedRate(new Runnable() {
            Set<AvroTopic<K, ? extends V>> topicsToSend = Collections.emptySet();
            @Override
            public void run() {
                if (topicsToSend.isEmpty()) {
                    topicsToSend = new HashSet<>(KafkaDataSubmitter.this.dataHandler.getCaches().keySet());
                }
                uploadCaches(topicsToSend);
                // still more data to send, do that immediately
                if (!topicsToSend.isEmpty()) {
                    executor.submit(this);
                }
            }
        }, 10L, 10L, TimeUnit.SECONDS);

        // If the connection was closed, check whether it can be opened again. Long random
        // timeout to prevent the server from overloading after a failure.
        // (expected value: 10 minutes)
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                checkClosed();
            }
        }, 0L, 5L + Math.round(10d * Math.random()), TimeUnit.MINUTES);

        // Run a heartbeat so the server is detected as being disconnected even if no data is being
        // uploaded
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (isConnected.get()) {
                    long now = System.currentTimeMillis();
                    if (now - lastConnection > 15_000L) {
                        if (KafkaDataSubmitter.this.sender.isConnected()) {
                            lastConnection = now;
                        } else {
                            senderDisconnected();
                        }
                    }
                }
            }
        }, 0L, 60L, TimeUnit.SECONDS);

        isConnected = new AtomicBoolean(true);
        lastConnection = 0L;
    }

    /**
     * Close the submitter eventually.
     *
     * Call {@link #join(long)} to wait for this to finish.
     */
    public void close() {
        executor.submit(new Runnable() {
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
                uploadCaches(new HashSet<>(caches.keySet()));
                if (isConnected.get()) {
                    try {
                        synchronized (trySendFuture) {
                            for (ScheduledFuture<?> future : trySendFuture.values()) {
                                future.cancel(true);
                            }
                            trySendFuture.clear();
                            for (Map.Entry<AvroTopic<K, V>, Queue<Record<K, V>>> records : trySendCache.entrySet()) {
                                sender(records.getKey()).send(new ArrayList<>(records.getValue()));
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
        executor.submit(new Runnable() {
            @Override
            public void run() {
                checkClosed();
            }
        });
    }

    /**
     * Upload a limited amount of data stored in the database which is not yet sent.
     */
    private void uploadCaches(Set<AvroTopic<K, ? extends V>> toSend) {
        boolean uploadingNotified = false;
        try {
            for (Map.Entry<AvroTopic<K, ? extends V>, ? extends DataCache<K, ? extends V>> entry : dataHandler.getCaches().entrySet()) {
                if (!toSend.contains(entry.getKey())) {
                    continue;
                }
                @SuppressWarnings("unchecked") // we can upload any record
                int sent = uploadCache((AvroTopic<K, V>)entry.getKey(), (DataCache<K, V>)entry.getValue(), SEND_LIMIT, uploadingNotified);
                if (sent < SEND_LIMIT) {
                    toSend.remove(entry.getKey());
                }
                uploadingNotified |= sent > 0;
            }
            if (uploadingNotified) {
                dataHandler.updateServerStatus(ServerStatusListener.Status.CONNECTED);
                lastConnection = System.currentTimeMillis();
            }
        } catch (IOException ex) {
            senderDisconnected();
        }
    }

    /**
     * Upload some data from a single table.
     * @return number of records sent.
     */
    private int uploadCache(AvroTopic<K, V> topic, DataCache<K, V> cache, int limit, boolean uploadingNotified) throws IOException {
        List<Record<K, V>> measurements = cache.unsentRecords(limit);

        if (measurements.isEmpty()) {
            return 0;
        }

        if (!uploadingNotified) {
            dataHandler.updateServerStatus(ServerStatusListener.Status.UPLOADING);
        }
        sender(topic).send(measurements);

        logger.debug("uploaded {} {} records", measurements.size(), topic.getName());
        return measurements.size();
    }

    /**
     * Check whether the connection was closed and try to reconnect.
     */
    private void checkClosed() {
        if (!isConnected.get() && (sender.isConnected() || sender.resetConnection())) {
            isConnected.set(true);
            lastConnection = System.currentTimeMillis();
            dataHandler.updateServerStatus(ServerStatusListener.Status.CONNECTED);
            logger.info("Sender reconnected");
        }
    }

    /**
     * Signal that the Kafka REST sender has disconnected.
     */
    private void senderDisconnected() {
        if (isConnected.compareAndSet(true, false)) {
            logger.warn("Sender disconnected");
            dataHandler.updateServerStatus(ServerStatusListener.Status.DISCONNECTED);
            if (sender.resetConnection()) {
                isConnected.set(true);
                logger.info("Sender reconnected");
                dataHandler.updateServerStatus(ServerStatusListener.Status.CONNECTED);
            } else {
                synchronized (trySendFuture) {
                    for (ScheduledFuture<?> future : trySendFuture.values()) {
                        future.cancel(false);
                    }
                    trySendFuture.clear();
                    trySendCache.clear();
                }
            }
        }
    }

    /**
     * Try to addMeasurement a message, without putting it in any permanent storage. Any failure may cause
     * messages to be lost. If the sender is disconnected, messages are immediately discarded.
     * @return whether the message was queued for sending.
     */
    public <W extends V> boolean trySend(final AvroTopic<K, W> topic, final long offset, final K deviceId, final W record) {
        if (!isConnected.get()) {
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
                        if (!isConnected.get()) {
                            return;
                        }

                        List<Record<K, V>> localRecords;
                        synchronized (trySendFuture) {
                            Queue<Record<K, V>> queue = trySendCache.get(topic);
                            trySendFuture.remove(topic);
                            localRecords = new ArrayList<>(queue);
                        }

                        try {
                            sender(castTopic).send(localRecords);
                            lastConnection = System.currentTimeMillis();
                        } catch (IOException e) {
                            senderDisconnected();
                        }
                    }
                }, 5L, TimeUnit.SECONDS));
            }
        }
        return true;
    }
}
