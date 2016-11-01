package org.radarcns.kafka.rest;

import android.support.annotation.NonNull;

import org.radarcns.data.Record;
import org.radarcns.data.RecordList;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.KafkaSender;
import org.radarcns.kafka.KafkaTopicSender;
import org.radarcns.util.RollingTimeAverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Send Avro Records to a Kafka REST Proxy.
 *
 * This queues messages for a specified amount of time and then sends all messages up to that time.
 */
public class ThreadedKafkaSender<K, V> implements KafkaSender<K, V> {
    private final static Logger logger = LoggerFactory.getLogger(ThreadedKafkaSender.class);
    private final static int RETRIES = 3;
    private final static int QUEUE_CAPACITY = 100;
    private final static long HEARTBEAT_TIMEOUT_MILLIS = 60_000L;
    private final static long HEARTBEAT_TIMEOUT_MARGIN = 10_000L;

    private final KafkaSender<K, V> sender;
    private final ScheduledExecutorService executor;
    private final RollingTimeAverage opsSent;
    private final RollingTimeAverage opsRequests;
    private final Queue<RecordList<K, V>> recordQueue;
    private long lastConnection;
    private boolean wasDisconnected;

    /**
     * Create a REST producer that caches some values
     *
     * @param sender Actual KafkaSender
     */
    public ThreadedKafkaSender(KafkaSender<K, V> sender) {
        this.sender = sender;
        this.recordQueue = new ArrayDeque<>(QUEUE_CAPACITY);
        this.wasDisconnected = true;
        this.lastConnection = 0L;
        this.executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                return new Thread(r, "Kafka REST Producer");
            }
        });
        opsSent = new RollingTimeAverage(20000L);
        opsRequests = new RollingTimeAverage(20000L);

        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                opsRequests.add(1);

                boolean success = sendHeartbeat();
                if (success) {
                    lastConnection = System.currentTimeMillis();
                } else {
                    logger.error("Failed to send message");
                    disconnect();
                }

                if (opsSent.hasAverage() && opsRequests.hasAverage()) {
                    logger.info("Sending {} messages in {} requests per second",
                            (int) Math.round(opsSent.getAverage()),
                            (int) Math.round(opsRequests.getAverage()));
                }
            }
        }, 0L, HEARTBEAT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private class ThreadedTopicSender<L extends K, W extends V> implements KafkaTopicSender<L, W>, Runnable {
        final KafkaTopicSender<L, W> topicSender;
        final List<List<Record<L, W>>> topicQueue;
        Future<?> topicFuture;

        private ThreadedTopicSender(AvroTopic<L, W> topic) throws IOException {
            topicSender = sender.sender(topic);
            topicQueue = new ArrayList<>();
            topicFuture = null;
        }

        /**
         * Send given key and record to a topic.
         * @param key key
         * @param value value with schema
         * @throws IOException if the producer is not connected.
         */
        @Override
        public void send(long offset, L key, W value) throws IOException {
            List<Record<L, W>> recordList = new ArrayList<>();
            recordList.add(new Record<>(offset, key, value));
            send(recordList);
        }

        @Override
        public synchronized void send(List<Record<L, W>> records) throws IOException {
            if (records.isEmpty()) {
                return;
            }
            if (!isConnected()) {
                throw new IOException("Producer is not connected");
            }
            synchronized (this) {
                topicQueue.add(records);
                if (topicFuture == null) {
                    topicFuture = executor.submit(this);
                }
            }

            logger.debug("Queue size: {}", recordQueue.size());
            notifyAll();
        }

        @Override
        public void clear() {
            synchronized (this) {
                topicFuture.cancel(false);
                topicFuture = null;
                topicQueue.clear();
            }
            topicSender.clear();
        }


        @Override
        public long getLastSentOffset() {
            return this.topicSender.getLastSentOffset();
        }

        @Override
        public void flush() throws IOException {
            Future<?> localFuture = null;
            synchronized (this) {
                if (topicFuture != null) {
                    localFuture = topicFuture;
                }
            }
            if (localFuture != null) {
                try {
                    localFuture.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            topicSender.flush();
        }

        @Override
        public void close() throws IOException {
            flush();
            topicSender.close();
        }

        @Override
        public void run() {
            List<List<Record<L, W>>> localQueue;
            synchronized (this) {
                localQueue = new ArrayList<>(topicQueue);
                topicQueue.clear();
                topicFuture = null;
            }

            opsRequests.add(1);

            for (List<Record<L, W>> records : localQueue) {
                opsSent.add(records.size());

                IOException exception = null;
                for (int i = 0; i < RETRIES; i++) {
                    try {
                        topicSender.send(records);
                        break;
                    } catch (IOException ex) {
                        exception = ex;
                    }
                }

                if (exception == null) {
                    synchronized (ThreadedKafkaSender.this) {
                        lastConnection = System.currentTimeMillis();
                    }
                } else {
                    logger.error("Failed to send message");
                    disconnect();
                    break;
                }
            }
        }
    }

    private boolean sendHeartbeat() {
        boolean success = false;
        for (int i = 0; !success && i < RETRIES; i++) {
            success = sender.isConnected();
        }
        return success;
    }

    private synchronized void disconnect() {
        this.wasDisconnected = true;
        this.recordQueue.clear();
        this.lastConnection = 0L;
        notifyAll();
    }

    @Override
    public synchronized boolean isConnected() {
        if (this.wasDisconnected) {
            return false;
        }
        if (System.currentTimeMillis() - lastConnection > HEARTBEAT_TIMEOUT_MILLIS + HEARTBEAT_TIMEOUT_MARGIN) {
            this.wasDisconnected = true;
            disconnect();
            return false;
        }

        return true;
    }

    @Override
    public boolean resetConnection() {
        if (isConnected()) {
            return true;
        } else if (sender.isConnected()) {
            synchronized (this) {
                lastConnection = System.currentTimeMillis();
                this.wasDisconnected = false;
                return true;
            }
        }
        return false;
    }

    @Override
    public <L extends K, W extends V> KafkaTopicSender<L, W> sender(AvroTopic<L, W> topic) throws IOException {
        return new ThreadedTopicSender<>(topic);
    }

    @Override
    public void close() throws IOException {
        executor.shutdown();
    }
}
