package org.radarcns.kafka;

import org.radarcns.data.DataCache;
import org.radarcns.data.DataHandler;
import org.radarcns.data.Record;
import org.radarcns.data.RecordIterable;
import org.radarcns.data.RecordList;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private final KafkaSender<K, V> directSender;
    private final ScheduledExecutorService executor;
    private final ConcurrentMap<AvroTopic, RecordList<K, V>> trySendCache;
    private final ConcurrentMap<AvroTopic, ScheduledFuture<?>> trySendFuture;
    private AtomicBoolean isConnected;
    private long lastConnection;

    public KafkaDataSubmitter(DataHandler<K, V> dataHandler, KafkaSender<K, V> sender, ThreadFactory threadFactory) {
        this.dataHandler = dataHandler;
        directSender = sender;
        directSender.configure(null);
        trySendCache = new ConcurrentHashMap<>();
        trySendFuture = new ConcurrentHashMap<>();
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
            @Override
            public void run() {
                final HashSet<AvroTopic> topicsToSend = new HashSet<>(KafkaDataSubmitter.this.dataHandler.getCaches().keySet());
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        uploadCaches(topicsToSend);
                        if (!topicsToSend.isEmpty()) {
                            executor.submit(this);
                        }
                    }
                });
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
                        if (directSender.isConnected()) {
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
                Map<AvroTopic, ? extends DataCache<K, V>> caches = dataHandler.getCaches();
                for (DataCache<K, V> cache : caches.values()) {
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
                            for (RecordList<K, V> records : trySendCache.values()) {
                                directSender.send(records);
                            }
                            trySendCache.clear();
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to addMeasurement latest measurements", e);
                    }
                }
                try {
                    directSender.close();
                } catch (IOException e1) {
                    logger.warn("failed to addMeasurement latest batches", e1);
                }
            }
        });
        executor.shutdown();
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
    private void uploadCaches(Set<AvroTopic> toSend) {
        boolean uploadingNotified = false;
        try {
            for (Map.Entry<AvroTopic, ? extends DataCache<K, V>> entry : dataHandler.getCaches().entrySet()) {
                int sent = uploadCache(entry.getKey(), entry.getValue(), SEND_LIMIT, uploadingNotified);
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
    private int uploadCache(AvroTopic topic, DataCache<K, V> cache, int limit, boolean uploadingNotified) throws IOException {
        RecordList<K, V> records = new RecordList<>(topic);
        try (RecordIterable<K, V> measurements = cache.unsentRecords(limit)) {
            if (measurements.iterator().hasNext() && !uploadingNotified) {
                dataHandler.updateServerStatus(ServerStatusListener.Status.UPLOADING);
            }
            for (Record<K, V> record : measurements) {
                records.add(record.offset, record.key, record.value);
            }
        }

        if (!records.isEmpty()) {
            directSender.send(records);
            cache.markSent(records.getLastOffset());
        }
        logger.debug("uploaded {} {} records", records.size(), topic.getName());
        return records.size();
    }

    /**
     * Check whether the connection was closed and try to reconnect.
     */
    private void checkClosed() {
        if (!isConnected.get() && (directSender.isConnected() || directSender.resetConnection())) {
            isConnected.set(true);
            lastConnection = System.currentTimeMillis();
            dataHandler.updateServerStatus(ServerStatusListener.Status.CONNECTED);
            logger.info("Sender reconnected");
        }
    }

    /**
     * Signal that the Kafka REST directSender has disconnected.
     */
    private void senderDisconnected() {
        if (isConnected.compareAndSet(true, false)) {
            logger.warn("Sender disconnected");
            dataHandler.updateServerStatus(ServerStatusListener.Status.DISCONNECTED);
            if (directSender.resetConnection()) {
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
    public boolean trySend(final AvroTopic topic, final long offset, final K deviceId, final V record) {
        if (!isConnected.get()) {
            return false;
        }
        synchronized (trySendFuture) {
            RecordList<K, V> records = trySendCache.get(topic);
            if (records == null) {
                records = new RecordList<>(topic);
                trySendCache.put(topic, records);
            }
            records.add(offset, deviceId, record);
            if (!trySendFuture.containsKey(topic)) {
                trySendFuture.put(topic, executor.schedule(new Runnable() {
                    @Override
                    public void run() {
                        if (!isConnected.get()) {
                            return;
                        }

                        RecordList<K, V> localRecords;
                        synchronized (trySendFuture) {
                            localRecords = trySendCache.remove(topic);
                            trySendFuture.remove(topic);
                        }

                        try {
                            directSender.send(localRecords);
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
