package org.radarcns.android;

import android.content.Context;

import org.apache.avro.generic.GenericRecord;
import org.radarcns.SchemaRetriever;
import org.radarcns.collect.KafkaSender;
import org.radarcns.collect.RecordList;
import org.radarcns.collect.Topic;
import org.radarcns.collect.rest.BatchedKafkaSender;
import org.radarcns.collect.rest.GenericRecordEncoder;
import org.radarcns.collect.rest.RestSender;
import org.radarcns.collect.rest.StringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stores data in databases and sends it to the server.
 */
public class DataHandler {
    private final static Logger logger = LoggerFactory.getLogger(DataHandler.class);
    private final long dataRetention;
    private final AtomicBoolean handledSenderDisconnected;
    private final URL kafkaUrl;
    private final SchemaRetriever schemaRetriever;
    private Map<Topic, MeasurementTable> tables;
    private Collection<ServerStatusListener> statusListeners;

    private Submitter submitter;

    public DataHandler(Context context, int dbAgeMillis, URL kafkaUrl, SchemaRetriever schemaRetriever, long dataRetentionMillis, Topic... topics) {
        this.kafkaUrl = kafkaUrl;
        this.schemaRetriever = schemaRetriever;
        tables = new HashMap<>(topics.length * 2);
        for (Topic topic : topics) {
            tables.put(topic, new MeasurementTable(context, topic, dbAgeMillis));
        }
        dataRetention = dataRetentionMillis;

        handledSenderDisconnected = new AtomicBoolean(false);
        submitter = null;
        statusListeners = new ArrayList<>();
    }

    /**
     * Start submitting data to the server.
     *
     * This can only be called if there is not already a submitter running.
     */
    public void start() {
        if (isStarted()) {
            throw new IllegalStateException("Cannot start submitter, it is already started");
        }
        for (ServerStatusListener listener : statusListeners) {
            listener.updateServerStatus(ServerStatusListener.Status.CONNECTING);
        }
        this.submitter = new Submitter();
    }

    public boolean isStarted() {
        return submitter != null;
    }

    /**
     * Pause sending any data.
     * This waits for any remaining data to be sent.
     */
    public void stop() {
        this.submitter.close();
        this.submitter = null;
    }

    /**
     * Sends any remaining data and closes the tables and connections.
     */
    public void close() throws InterruptedException {
        if (this.submitter != null) {
            this.submitter.close();
            this.submitter.join(5_000L);
            this.submitter = null;
        }
        cleanTables();
        for (MeasurementTable table : tables.values()) {
            table.close();
        }
    }

    public synchronized void addStatusListener(ServerStatusListener listener) {
        statusListeners.add(listener);
    }
    public synchronized void removeStatusListener(ServerStatusListener listener) {
        statusListeners.remove(listener);
    }

    /**
     * Separate thread to read from the database and send it to the Kafka server. Also cleans the
     * database.
     *
     * It uses a set of timers to send data and clean the databases.
     */
    private class Submitter {
        private final static int SEND_LIMIT = 1000;
        private final KafkaSender<String, GenericRecord> directSender;
        private final KafkaSender<String, GenericRecord> senderWrapper;
        private final ScheduledExecutorService executor;

        Submitter() {
            directSender = new RestSender<>(kafkaUrl, schemaRetriever, new StringEncoder(), new GenericRecordEncoder());
            senderWrapper = new BatchedKafkaSender<>(directSender, 1000, 1000);
            senderWrapper.configure(null);
            executor = Executors.newSingleThreadScheduledExecutor();
            // Remove old data from tables infrequently
            executor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    cleanTables();
                }
            }, 0L, 1L, TimeUnit.HOURS);
            // Upload very frequently.
            executor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    final HashSet<Topic> topicsToSend = new HashSet<>(tables.keySet());
                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            if (directSender.isConnected()) {
                                uploadTables(topicsToSend);
                                if (!topicsToSend.isEmpty()) {
                                    executor.submit(this);
                                }
                            } else {
                                senderDisconnected();
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
        }

        /**
         * Close the submitter eventually.
         *
         * Call {@link #join(long)} to wait for this to finish.
         */
        void close() {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    for (MeasurementTable table : tables.values()) {
                        table.flush();
                    }
                    uploadTables(new HashSet<>(tables.keySet()));
                    try {
                        senderWrapper.close();
                    } catch (IOException e1) {
                        logger.warn("failed to send latest batches", e1);
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
        void join(long millis) throws InterruptedException {
            executor.awaitTermination(millis, TimeUnit.MILLISECONDS);
        }

        /**
         * Check the connection status eventually.
         */
        void checkConnection() {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    checkConnection();
                }
            });
        }

        /**
         * Upload a limited amount of data stored in the database which is not yet sent.
         */
        private void uploadTables(Set<Topic> toSend) {
            boolean uploadingNotified = false;
            try {
                for (Map.Entry<Topic, MeasurementTable> entry : tables.entrySet()) {
                    int sent = uploadTable(entry.getKey(), entry.getValue(), SEND_LIMIT, uploadingNotified);
                    if (sent < SEND_LIMIT) {
                        toSend.remove(entry.getKey());
                    }
                    uploadingNotified |= sent > 0;
                }
                if (uploadingNotified) {
                    for (ServerStatusListener listener : statusListeners) {
                        listener.updateServerStatus(ServerStatusListener.Status.CONNECTED);
                    }
                }
            } catch (IOException ex) {
                senderDisconnected();
            }
        }

        /**
         * Upload some data from a single table.
         * @return number of records sent.
         */
        private int uploadTable(Topic topic, MeasurementTable table, int limit, boolean uploadingNotified) throws IOException {
            RecordList<String, GenericRecord> records = new RecordList<>(topic);
            try (MeasurementIterator measurements = table.getUnsentMeasurements(limit)) {
                if (measurements.hasNext() && !uploadingNotified) {
                    for (ServerStatusListener listener : statusListeners) {
                        listener.updateServerStatus(ServerStatusListener.Status.UPLOADING);
                    }
                }
                for (MeasurementTable.Measurement record : measurements) {
                    records.add(record.offset, record.key, record.value);
                }
            }
            directSender.send(records);
            if (!records.isEmpty()) {
                table.markSent(records.getLastOffset());
            }
            logger.debug("uploaded {} {} records", records.size(), topic.getName());
            return records.size();
        }

        /**
         * Check whether the connection was closed and try to reconnect.
         */
        private void checkClosed() {
            if (!senderWrapper.isConnected()) {
                boolean oldValue = handledSenderDisconnected.getAndSet(false);
                if (senderWrapper.resetConnection()) {
                    for (ServerStatusListener listener : statusListeners) {
                        listener.updateServerStatus(ServerStatusListener.Status.CONNECTED);
                    }
                    logger.info("Sender reconnected");
                } else {
                    handledSenderDisconnected.set(oldValue);
                }
            }
        }

        /**
         * Signal that the Kafka REST directSender has disconnected.
         */
        private void senderDisconnected() {
            if (handledSenderDisconnected.compareAndSet(false, true)) {
                logger.warn("Sender disconnected");
                for (ServerStatusListener listener : statusListeners) {
                    listener.updateServerStatus(ServerStatusListener.Status.DISCONNECTED);
                }
                handledSenderDisconnected.set(false);
                if (senderWrapper.resetConnection()) {
                    logger.info("Sender reconnected");
                    for (ServerStatusListener listener : statusListeners) {
                        listener.updateServerStatus(ServerStatusListener.Status.CONNECTED);
                    }
                } else {
                    handledSenderDisconnected.set(true);
                }
            }
        }

        private boolean trySend(final Topic topic, final long offset, final String deviceId, final GenericRecord record) {
            if (!senderWrapper.isConnected()) {
                return false;
            }
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    if (senderWrapper.isConnected()) {
                        try {
                            senderWrapper.send(topic, offset, deviceId, record);
                        } catch (IOException e) {
                            senderDisconnected();
                        }
                    }
                }
            });
            return true;
        }
    }

    /**
     * Remove old measurements from the database.
     */
    private void cleanTables() {
        double timestamp = (System.currentTimeMillis() - dataRetention) / 1000d;
        for (MeasurementTable table : tables.values()) {
            table.removeBeforeTimestamp(timestamp);
        }
    }

    public boolean trySend(Topic topic, long offset, String deviceId, GenericRecord record) {
        return submitter != null && submitter.trySend(topic, offset, deviceId, record);
    }

    /**
     * Check the connection with the server.
     *
     * Updates will be given to any listeners registered to
     * {@link #addStatusListener(ServerStatusListener)}.
     */
    public void checkConnection() {
        if (submitter == null) {
            start();
        }
        submitter.checkConnection();
    }

    /** Send a record and add it to the local table. */
    public void sendAndAddToTable(Topic topic, String deviceId, double timestamp, Object... values) {
        GenericRecord record = topic.createSimpleRecord(timestamp, values);
        Object[] valueArray = new Object[values.length / 2 + 2];
        valueArray[0] = timestamp;
        valueArray[1] = record.get("timeReceived");
        for (int i = 0; i < values.length; i += 2) {
            valueArray[i / 2 + 2] = values[i + 1];
        }
        tables.get(topic).addMeasurement(deviceId, valueArray);
    }

    /**
     * Get the table of a given topic
     */
    public MeasurementTable getTable(Topic topic) {
        return this.tables.get(topic);
    }
}
