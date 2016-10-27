package org.radarcns.android;

import android.content.Context;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.data.DataCache;
import org.radarcns.data.DataHandler;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.KafkaDataSubmitter;
import org.radarcns.kafka.KafkaSender;
import org.radarcns.key.MeasurementKey;
import org.radarcns.kafka.SchemaRetriever;
import org.radarcns.kafka.rest.RestSender;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.kafka.rest.SpecificRecordEncoder;

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
public class TableDataHandler implements DataHandler<MeasurementKey, SpecificRecord> {
    private final long dataRetention;
    private final URL kafkaUrl;
    private final SchemaRetriever schemaRetriever;
    private final ThreadFactory threadFactory;
    private final Map<AvroTopic, MeasurementTable> tables;
    private final Collection<ServerStatusListener> statusListeners;
    private ServerStatusListener.Status status;

    private KafkaDataSubmitter<MeasurementKey, SpecificRecord> submitter;

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
            KafkaSender<MeasurementKey, SpecificRecord> sender = new RestSender<>(kafkaUrl, schemaRetriever, new SpecificRecordEncoder(), new SpecificRecordEncoder());
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
        for (DataCache<MeasurementKey, SpecificRecord> table : tables.values()) {
            table.close();
        }
    }

    @Override
    public void clean() {
        long timestamp = (System.currentTimeMillis() - dataRetention);
        for (DataCache<MeasurementKey, SpecificRecord> table : tables.values()) {
            table.removeBeforeTimestamp(timestamp);
        }
    }

    public boolean trySend(AvroTopic topic, long offset, MeasurementKey deviceId, SpecificRecord record) {
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
     * Get the table of a given topic
     */
    @Override
    public MeasurementTable getCache(AvroTopic topic) {
        return this.tables.get(topic);
    }

    @Override
    public void addMeasurement(DataCache<MeasurementKey, SpecificRecord> table, MeasurementKey key, SpecificRecord value) {
        table.addMeasurement(key, value);
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
