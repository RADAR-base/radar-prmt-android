package org.radarcns.kafka.rest;

import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.KafkaSender;
import org.radarcns.data.RecordList;
import org.radarcns.util.RollingTimeAverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Properties;
import java.util.Queue;

/**
 * Send Avro Records to a Kafka REST Proxy.
 *
 * This queues messages for a specified amount of time and then sends all messages up to that time.
 */
public class ThreadedKafkaSender<K, V> extends Thread implements KafkaSender<K, V> {
    private final static Logger logger = LoggerFactory.getLogger(ThreadedKafkaSender.class);
    private final static int RETRIES = 3;
    private final static int QUEUE_CAPACITY = 100;
    private final static long HEARTBEAT_TIMEOUT_MILLIS = 60_000L;
    private final static long HEARTBEAT_TIMEOUT_MARGIN = 10_000L;

    private final KafkaSender<K, V> sender;
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
        super("Kafka REST Producer");
        this.sender = sender;
        this.recordQueue = new ArrayDeque<>(QUEUE_CAPACITY);
        this.wasDisconnected = true;
        this.lastHeartbeat = 0L;
        this.lastConnection = 0L;
        this.isSending = false;
    }

    /**
     * Actually make REST requests.
     *
     * The offsets of the sent messages are added to a
     */
    public void run() {
        RollingTimeAverage opsSent = new RollingTimeAverage(20000L);
        RollingTimeAverage opsRequests = new RollingTimeAverage(20000L);
        try {
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
                    success = sendMessages(records);
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
    }

    private boolean sendMessages(RecordList<K, V> records) {
        IOException exception = null;
        for (int i = 0; i < RETRIES; i++) {
            try {
                sender.send(records);
                break;
            } catch (IOException ex) {
                exception = ex;
            }
        }
        return exception == null;
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
    public synchronized void clear() {
        sender.clear();
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
    public void configure(Properties properties) {
        this.resetConnection();
        start();
        sender.configure(properties);
    }

    /**
     * Send given key and record to a topic.
     * @param topic topic name
     * @param key key
     * @param value value with schema
     * @throws IllegalStateException if the producer is not connected.
     */
    @Override
    public void send(AvroTopic topic, long offset, K key, V value) throws IOException {
        RecordList<K, V> recordList = new RecordList<>(topic);
        recordList.add(offset, key, value);
        send(recordList);
    }

    @Override
    public synchronized void send(RecordList<K, V> records) throws IOException {
        if (records.isEmpty()) {
            return;
        }
        if (!isConnected()) {
            throw new IOException("Producer is not connected");
        }
        recordQueue.add(records);
        logger.debug("Queue size: {}", recordQueue.size());
        notifyAll();
    }

    @Override
    public long getLastSentOffset(AvroTopic topic) {
        return this.sender.getLastSentOffset(topic);
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
            sender.flush();
        }
    }

    @Override
    public void close() throws IOException {
        IOException ex = null;
        try {
            try {
                flush();
            } catch (IOException e) {
                logger.warn("Cannot flush buffer", e);
                ex = e;
            }
            interrupt();
            join();
        } catch (InterruptedException e) {
            ex = new IOException("Sending interrupted.");
            Thread.currentThread().interrupt();
        } finally {
            try {
                sender.close();
            } catch (IOException e) {
                logger.warn("Cannot close sender", e);
                ex = e;
            }
        }
        if (ex != null) {
            throw ex;
        }
    }
}
