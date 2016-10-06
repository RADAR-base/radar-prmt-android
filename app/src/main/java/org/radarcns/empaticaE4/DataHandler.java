package org.radarcns.empaticaE4;

import android.content.Context;

import org.apache.avro.generic.GenericRecord;
import org.radarcns.SchemaRetriever;
import org.radarcns.android.MeasurementIterator;
import org.radarcns.android.MeasurementTable;
import org.radarcns.collect.RestProducer;
import org.radarcns.collect.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stores data in databases and sends it to the server.
 */
class DataHandler {
    private final static Logger logger = LoggerFactory.getLogger(DataHandler.class);
    private final RestProducer sender;
    private final long dataRetention;
    private final AtomicBoolean handledSenderDisconnected;
    private Map<Topic, MeasurementTable> tables;
    private Map<Topic, Long> lastOffsetUploaded;
    private Collection<ServerStatusListener> statusListeners;

    private Submitter submitter;

    DataHandler(Context context, int dbAgeMillis, URL kafkaUrl, int restAgeMillis, int restMaxBatchSize, SchemaRetriever schemaRetriever, long dataRetentionMillis, Topic... topics) {
        sender = new RestProducer(kafkaUrl, restAgeMillis, restMaxBatchSize, schemaRetriever);
        sender.start();

        tables = new HashMap<>(topics.length * 2);
        lastOffsetUploaded = new HashMap<>(topics.length * 2);
        for (Topic topic : topics) {
            tables.put(topic, new MeasurementTable(context, topic, dbAgeMillis));
            lastOffsetUploaded.put(topic, 0L);
        }
        dataRetention = dataRetentionMillis;

        handledSenderDisconnected = new AtomicBoolean(false);
        submitter = null;
        statusListeners = new ArrayList<>();
    }


    void start() {
        if (submitter != null) {
            throw new IllegalStateException("Cannot start submitter, it is already started");
        }
        for (ServerStatusListener listener : statusListeners) {
            listener.updateStatus(ServerStatusListener.Status.CONNECTING);
        }
        this.submitter = new Submitter();
        this.submitter.start();
    }

    void addStatusListener(ServerStatusListener listener) {
        statusListeners.add(listener);
    }

    /**
     * Separate thread to read from the database and send it to the Kafka server. Also cleans the
     * database.
     */
    private class Submitter extends Thread {
        private final static long CLEAN_INTERVAL = 3_600_000L; // 1 hour
        private final static long UPLOAD_INTERVAL = 1_000L; // 1 second
        private final static long CLOSED_CHECK_INTERVAL = 600_000L; // 10 minutes
        private final static long SENDER_FLUSH_INTERVAL = 10_000L; // 10 seconds

        boolean isClosed;
        private long lastClean;
        private long lastUpload;
        private long lastClosedCheck;
        private long lastSenderFlush;

        Submitter() {
            super("DataSubmitter");
            isClosed = false;
            lastClean = 0L;
            lastUpload = 0L;
            lastClosedCheck = 0L;
            lastSenderFlush = System.currentTimeMillis();
        }

        public void run() {
            try {
                while (true) {
                    long nextClean, nextUpload, nextClosedCheck, nextSenderFlush, nextEvent, now;
                    synchronized (this) {
                        nextClean = lastClean + CLEAN_INTERVAL;
                        nextUpload = lastUpload + UPLOAD_INTERVAL;
                        nextClosedCheck = lastClosedCheck + CLOSED_CHECK_INTERVAL;
                        nextSenderFlush = lastSenderFlush + SENDER_FLUSH_INTERVAL;
                        nextEvent = Math.min(nextSenderFlush, Math.min(nextClean, Math.min(nextUpload, nextClosedCheck)));
                        now = System.currentTimeMillis();
                        while (!this.isClosed && nextEvent > now) {
                            wait(nextEvent - now);
                            now = System.currentTimeMillis();
                        }
                        if (this.isClosed) {
                            for (MeasurementTable table : tables.values()) {
                                table.flush();
                            }
                            uploadTables();
                            sender.triggerFlush();
                            updateTables();
                            return;
                        }
                    }
                    if (nextEvent == nextClean) {
                        cleanTables();
                        lastClean = now;
                    } else if (nextEvent == nextUpload) {
                        if (sender.isConnected()) {
                            uploadTables();
                        } else {
                            senderDisconnected();
                        }
                        lastUpload = now;
                    } else if (nextEvent == nextClosedCheck) {
                        checkClosed();
                        lastClosedCheck = now;
                    } else if (nextEvent == nextSenderFlush) {
                        sender.flush();
                        for (ServerStatusListener listener : statusListeners) {
                            listener.updateStatus(ServerStatusListener.Status.CONNECTED);
                        }
                        lastSenderFlush = now;
                    }
                }
            } catch (InterruptedException e) {
                logger.warn("DataHandler interrupted", e);
            }
        }

        synchronized void close() {
            isClosed = true;
            notify();
        }

        synchronized void checkConnection() {
            lastClosedCheck = 0L;
            notify();
        }

        private void uploadTables() throws InterruptedException {
            boolean hasSent = false;
            try {
                for (Map.Entry<Topic, MeasurementTable> entry : tables.entrySet()) {
                    hasSent |= uploadTable(entry.getKey(), entry.getValue()) > 0;
                }
            } catch (IllegalStateException ex) {
                senderDisconnected();
            }
            if (hasSent) {
                for (ServerStatusListener listener : statusListeners) {
                    listener.updateStatus(ServerStatusListener.Status.UPLOADING);
                }
            }
        }

        private int uploadTable(Topic topic, MeasurementTable table) {
            int sent = 0;
            try (MeasurementIterator measurements = table.getUnsentMeasurements(lastOffsetUploaded.get(topic), 5000)) {
                long lastOffset = lastOffsetUploaded.get(topic);
                for (MeasurementTable.Measurement record : measurements) {
                    lastOffset = record.offset;
                    sender.send(record.offset, topic.getName(), record.key, record.value);
                    sent++;
                }
                lastOffsetUploaded.put(topic, lastOffset);
            }
            logger.debug("uploaded {} {} records", sent, topic.getName());
            return sent;
        }
    }

    private void cleanTables() {
        double timestamp = (System.currentTimeMillis() - dataRetention) / 1000d;
        for (MeasurementTable table : tables.values()) {
            table.removeBeforeTimestamp(timestamp);
        }
    }

    private void senderDisconnected() {
        if (handledSenderDisconnected.compareAndSet(false, true)) {
            logger.warn("Sender disconnected");
            for (ServerStatusListener listener : statusListeners) {
                listener.updateStatus(ServerStatusListener.Status.DISCONNECTED);
            }
            updateTables();
            for (Topic topic : tables.keySet()) {
                lastOffsetUploaded.put(topic, 0L);
            }
            handledSenderDisconnected.set(false);
            if (sender.resetConnection()) {
                logger.info("Sender reconnected");
                for (ServerStatusListener listener : statusListeners) {
                    listener.updateStatus(ServerStatusListener.Status.CONNECTED);
                }
            } else {
                handledSenderDisconnected.set(true);
            }
        }
    }

    private void updateTables() {
        for (Map.Entry<Topic, MeasurementTable> entry : tables.entrySet()) {
            int marked = entry.getValue().markSent(sender.getLastSentOffset(entry.getKey().getName()));
            logger.debug("marked {} {} records as sent", marked, entry.getKey().getName());
        }
    }

    void pause() throws InterruptedException {
        this.submitter.close();
        this.submitter.join();
        this.submitter = null;
    }

    void checkConnection() {
        if (submitter == null) {
            start();
        }
        submitter.checkConnection();
    }

    private void checkClosed() throws InterruptedException {
        if (!sender.isConnected()) {
            boolean oldValue = handledSenderDisconnected.getAndSet(false);
            if (sender.resetConnection()) {
                for (ServerStatusListener listener : statusListeners) {
                    listener.updateStatus(ServerStatusListener.Status.CONNECTED);
                }
                logger.info("Sender reconnected");
            } else {
                handledSenderDisconnected.set(oldValue);
            }
        }
    }

    void sendAndAddToTable(Topic topic, String deviceId, double timestamp, Object... values) {
        GenericRecord record = topic.createSimpleRecord(timestamp, values);
        Object[] valueArray = new Object[values.length / 2 + 2];
        valueArray[0] = timestamp;
        valueArray[1] = record.get("timeReceived");
        for (int i = 0; i < values.length; i += 2) {
            valueArray[i / 2 + 2] = values[i + 1];
        }
        tables.get(topic).addMeasurement(deviceId, valueArray);
    }

    boolean trySend(long l, String name, String deviceId, GenericRecord record) {
        if (sender.isConnected()) {
            try {
                sender.send(l, name, deviceId, record);
                return true;
            } catch (IllegalStateException ex) {
                senderDisconnected();
                return false;
            }
        }
        return false;
    }

    void close() throws InterruptedException {
        if (this.submitter != null) {
            this.submitter.close();
            this.submitter.join();
            this.submitter = null;
        }
        cleanTables();
        for (MeasurementTable table : tables.values()) {
            table.close();
        }
        sender.close();
    }

    MeasurementTable getTable(Topic topic) {
        return this.tables.get(topic);
    }
}
