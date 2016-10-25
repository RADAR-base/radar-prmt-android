package org.radarcns.android;

import android.content.Context;

import org.apache.avro.generic.GenericRecord;
import org.radarcns.data.DataCache;
import org.radarcns.data.DataHandler;
import org.radarcns.kafka.KafkaSender;
import org.radarcns.kafka.SchemaRetriever;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.KafkaDataSubmitter;
import org.radarcns.kafka.rest.GenericRecordEncoder;
import org.radarcns.kafka.rest.RestSender;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.kafka.rest.StringEncoder;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

/**
 * Stores data in databases and sends it to the server.
 */
public class TableDataHandler implements DataHandler<String, GenericRecord> {
    private final long dataRetention;
    private final URL kafkaUrl;
    private final SchemaRetriever schemaRetriever;
    private final ThreadFactory threadFactory;
    private final Map<AvroTopic, MeasurementTable> tables;
    private final Collection<ServerStatusListener> statusListeners;
    private ServerStatusListener.Status status;

    private KafkaDataSubmitter<String, GenericRecord> submitter;

    /**
     * Create a data handler. If kafkaUrl is null, data will only be stored to disk, not uploaded.
     */
    public TableDataHandler(Context context, int dbAgeMillis, URL kafkaUrl, SchemaRetriever schemaRetriever, long dataRetentionMillis, AvroTopic... topics) {
        this.kafkaUrl = kafkaUrl;
        this.schemaRetriever = schemaRetriever;
        tables = new HashMap<>(topics.length * 2);
        for (AvroTopic topic : topics) {
            tables.put(topic, new MeasurementTable(context, topic, dbAgeMillis));
        }
        dataRetention = dataRetentionMillis;

        submitter = null;
        statusListeners = new ArrayList<>();
        if (kafkaUrl != null) {
            this.threadFactory = new AndroidThreadFactory("DataHandler", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        } else {
            this.threadFactory = null;
        }
        updateServerStatus(ServerStatusListener.Status.READY);
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
            updateServerStatus(ServerStatusListener.Status.CONNECTING);
            KafkaSender<String, GenericRecord> sender = new RestSender<>(kafkaUrl, schemaRetriever, new StringEncoder(), new GenericRecordEncoder());
            this.submitter = new KafkaDataSubmitter<>(this, sender, threadFactory);
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
     * @throws IOException if the tables cannot be flushed
     */
    public void close() throws IOException {
        if (this.submitter != null) {
            try {
                this.submitter.close();
                this.submitter.join(5_000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                this.submitter = null;
            }
        }
        clean();
        for (DataCache<String, GenericRecord> table : tables.values()) {
            table.close();
        }
    }

    @Override
    public void clean() {
        long timestamp = (System.currentTimeMillis() - dataRetention);
        for (DataCache<String, GenericRecord> table : tables.values()) {
            table.removeBeforeTimestamp(timestamp);
        }
    }

    public boolean trySend(AvroTopic topic, long offset, String deviceId, GenericRecord record) {
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
    @Override
    public MeasurementTable getCache(AvroTopic topic) {
        return this.tables.get(topic);
    }

    public Map<AvroTopic, MeasurementTable> getCaches() {
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

    @Override
    public void updateServerStatus(ServerStatusListener.Status status) {
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
