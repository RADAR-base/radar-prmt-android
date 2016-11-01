package org.radarcns.kafka.rest;

import android.support.annotation.NonNull;

import org.radarcns.data.Record;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.KafkaSender;
import org.radarcns.data.RecordList;
import org.radarcns.kafka.KafkaTopicSender;
import org.radarcns.util.RollingTimeAverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private long lastHeartbeat;
    private final Queue<RecordList<K, V>> recordQueue;
    private long lastConnection;
    private boolean wasDisconnected;
    private boolean isSending;

    /**
     * Create a REST producer that caches some values
     *
     * @param sender Actual KafkaSender
     */
    public ThreadedKafkaSender(KafkaSender<K, V> sender) {
        this.sender = sender;
        this.recordQueue = new ArrayDeque<>(QUEUE_CAPACITY);
        this.wasDisconnected = true;
        this.lastHeartbeat = 0L;
        this.lastConnection = 0L;
        this.isSending = false;
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
                lastHeartbeat = System.currentTimeMillis();
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

    /**
     * Actually make REST requests.
     *
     * The offsets of the sent messages are added to a
     */
    public void run() {
        Map<AvroTopic<K, V>, KafkaTopicSender<K, V>> topicSenders = new HashMap<>();
        try {
            resetConnection();
            while (true) {
                RecordList<K, V> records;

                synchronized (this) {
                    long nextHeartbeatEvent = Math.max(lastConnection, lastHeartbeat) + HEARTBEAT_TIMEOUT_MILLIS;
                    long now = System.currentTimeMillis();
                    while (this.recordQueue.isEmpty() && nextHeartbeatEvent > now) {
                        wait(nextHeartbeatEvent - now);
                        now = System.currentTimeMillis();
                    }
                    records = this.recordQueue.poll();
                    isSending = records != null;
                }

                opsRequests.add(1);
                boolean success;
                if (records != null) {
                    opsSent.add(records.size());
                    success = sendMessages(records, topicSenders);
                } else {
                    success = sendHeartbeat();
                }

                synchronized (this) {
                    if (records == null) {
                        lastHeartbeat = System.currentTimeMillis();
                    }
                    if (success) {
                        lastConnection = System.currentTimeMillis();
                    } else {
                        logger.error("Failed to send message");
                        disconnect();
                    }
                    isSending = false;
                    notifyAll();
                }

                if (opsSent.hasAverage() && opsRequests.hasAverage()) {
                    logger.info("Sending {} messages in {} requests per second",
                            (int) Math.round(opsSent.getAverage()),
                            (int) Math.round(opsRequests.getAverage()));
                }
            }
        } catch (InterruptedException e) {
            // exit loop and reset interrupt status
            Thread.currentThread().interrupt();
        }
        for (KafkaTopicSender<K, V> topicSender : topicSenders.values()) {
            try {
                topicSender.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
            synchronized (topicQueue) {
                topicQueue.add(records);
                if (topicFuture == null) {
                    topicFuture = executor.submit(this);
                }
            }

            logger.debug("Queue size: {}", recordQueue.size());
            notifyAll();
        }

        @Override
        public synchronized void clear() {
            topicSender.clear();
        }


        @Override
        public long getLastSentOffset() {
            return this.topicSender.getLastSentOffset();
        }

        @Override
        public synchronized void flush() throws IOException {
            try {
                if (!isConnected()) {
                    throw new IOException("Not connected.");
                }
                while (!this.isInterrupted() && (isSending || !this.recordQueue.isEmpty())) {
                    wait();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException(ex);
            } finally {
                topicSender.flush();
            }
        }

        @Override
        public void close() throws IOException {
            topicSender.close();
        }

        @Override
        public void run() {
            List<List<Record<L, W>>> localQueue;
            synchronized (topicQueue) {
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
                    lastConnection = System.currentTimeMillis();
                } else {
                    logger.error("Failed to send message");
                    disconnect();
                    break;
                }
            }
            isSending = false;
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
                lastHeartbeat = System.currentTimeMillis();
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
