package org.radarcns.android;

import android.content.Context;
import android.support.annotation.NonNull;

import org.apache.avro.generic.GenericRecord;
import org.radarcns.SchemaRetriever;
import org.radarcns.collect.Topic;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

/**
 * Stores data in databases and sends it to the server.
 */
public class DataHandler {
    private final long dataRetention;
    private final URL kafkaUrl;
    private final SchemaRetriever schemaRetriever;
    private final ThreadFactory threadFactory;
    private final Map<Topic, MeasurementTable> tables;
    private final Collection<ServerStatusListener> statusListeners;
    private ServerStatusListener.Status status;

    private KafkaDataSubmitter submitter;

    /**
     * Create a data handler. If kafkaUrl is null, data will only be stored to disk, not uploaded.
     */
    public DataHandler(Context context, int dbAgeMillis, URL kafkaUrl, SchemaRetriever schemaRetriever, long dataRetentionMillis, Topic... topics) {
        this.kafkaUrl = kafkaUrl;
        this.schemaRetriever = schemaRetriever;
        tables = new HashMap<>(topics.length * 2);
        for (Topic topic : topics) {
            tables.put(topic, new MeasurementTable(context, topic, dbAgeMillis));
        }
        dataRetention = dataRetentionMillis;

        submitter = null;
        statusListeners = new ArrayList<>();
        if (kafkaUrl != null) {
            this.threadFactory = new ThreadFactory() {
                @Override
                public Thread newThread(@NonNull Runnable r) {
                    return new Thread(r, "DataHandler");
                }
            };
        } else {
            this.threadFactory = null;
        }
        updateStatus(ServerStatusListener.Status.INACTIVE);
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
        if (kafkaUrl != null) {
            updateStatus(ServerStatusListener.Status.CONNECTING);
            this.submitter = new KafkaDataSubmitter(this, kafkaUrl, schemaRetriever, threadFactory);
        }
    }

    public boolean isStarted() {
        return submitter != null;
    }

    /**
     * Pause sending any data.
     * This waits for any remaining data to be sent.
     */
    public void stop() {
        if (submitter != null) {
            this.submitter.close();
            this.submitter = null;
        }
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

    /**
     * Remove old measurements from the database.
     */
    void cleanTables() {
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
        if (submitter != null) {
            submitter.checkConnection();
        }
    }


    /**
     * Send a record and add it to the local table.
     *
     * Values are passed as tuples with the Schema.Field of the topic schema and then the value, e.g.
     * {@code
     * Schema schema = table.getTopic().getValueSchema();
     * Schema.Field myField1 = schema.getField("myField1");
     * Schema.Field myField2 = schema.getField("myField2");
     * dataHandler.addMeasurement(table, device, time, myField1, myValue1, myField2, myValue2);
     * }
     * For performance reasons, re-use the Schema.Field objects when adding any measurements.
     *
     * @param table table to add measurement to
     * @param deviceId device ID the measurement belongs to
     * @param timestamp timestamp that the measurement device reported
     * @param values values to add, alternating between the Schema.Field of the topic that the value belongs and the values themselves.
     */
    public void addMeasurement(MeasurementTable table, String deviceId, double timestamp, Object... values) {
        table.addMeasurement(deviceId, timestamp, System.currentTimeMillis() / 1000d, values);
    }

    /**
     * Get the table of a given topic
     */
    public MeasurementTable getTable(Topic topic) {
        return this.tables.get(topic);
    }

    Map<Topic, MeasurementTable> getTables() {
        return tables;
    }

    public void addStatusListener(ServerStatusListener listener) {
        synchronized (statusListeners) {
            statusListeners.add(listener);
        }
    }
    public void removeStatusListener(ServerStatusListener listener) {
        synchronized (statusListeners) {
            statusListeners.remove(listener);
        }
    }

    void updateStatus(ServerStatusListener.Status status) {
        synchronized (statusListeners) {
            for (ServerStatusListener listener : statusListeners) {
                listener.updateServerStatus(status);
            }
            this.status = status;
        }
    }

    public ServerStatusListener.Status getStatus() {
        synchronized (statusListeners) {
            return this.status;
        }
    }
}
