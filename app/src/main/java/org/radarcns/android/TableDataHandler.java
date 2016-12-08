package org.radarcns.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.data.DataCache;
import org.radarcns.data.DataHandler;
import org.radarcns.data.SpecificRecordEncoder;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.KafkaDataSubmitter;
import org.radarcns.kafka.KafkaSender;
import org.radarcns.kafka.SchemaRetriever;
import org.radarcns.kafka.rest.RestSender;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadFactory;

/**
 * Stores data in databases and sends it to the server.
 */
public class TableDataHandler implements DataHandler<MeasurementKey, SpecificRecord> {
    private final static Logger logger = LoggerFactory.getLogger(TableDataHandler.class);

    private final long dataRetention;
    private final Context context;
    private final URL kafkaUrl;
    private final SchemaRetriever schemaRetriever;
    private final ThreadFactory threadFactory;
    private final Map<AvroTopic<MeasurementKey, ? extends SpecificRecord>, MeasurementTable<? extends SpecificRecord>> tables;
    private final Set<ServerStatusListener> statusListeners;
    private final BroadcastReceiver connectivityReceiver;
    private ServerStatusListener.Status status;
    private Map<String, Integer> lastNumberOfRecordsSent = new TreeMap<>();

    private KafkaDataSubmitter<MeasurementKey, SpecificRecord> submitter;

    /**
     * Create a data handler. If kafkaUrl is null, data will only be stored to disk, not uploaded.
     */
    @SafeVarargs
    public TableDataHandler(Context context, int dbAgeMillis, URL kafkaUrl, SchemaRetriever schemaRetriever, long dataRetentionMillis,
                            long kafkaUploadRate, long kafkaCleanRate, long kafkaRecordsSendLimit, long senderConnectionTimeout,
                            AvroTopic<MeasurementKey, ? extends SpecificRecord>... topics) {
        this.context = context;
        this.kafkaUrl = kafkaUrl;
        this.schemaRetriever = schemaRetriever;
        tables = new HashMap<>(topics.length * 2);
        for (AvroTopic<MeasurementKey, ? extends SpecificRecord> topic : topics) {
            tables.put(topic, new MeasurementTable<>(context, topic, dbAgeMillis));
        }
        dataRetention = dataRetentionMillis;

        submitter = null;
        statusListeners = new HashSet<>();
        if (kafkaUrl != null) {
            this.threadFactory = new AndroidThreadFactory("DataHandler", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        } else {
            this.threadFactory = null;
        }
        if (kafkaUrl != null) {
            updateServerStatus(Status.READY);
            connectivityReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                        if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
                            logger.info("Network disconnected, stopping data sender.");
                            stop();
                        } else if (!isStarted()) {
                            logger.info("Network connected, starting data sender.");
                            start();
                        }
                    }
                }
            };
            context.registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        } else {
            connectivityReceiver = null;
            updateServerStatus(Status.DISABLED);
        }
    }

    private boolean isDataConnected() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo().isConnectedOrConnecting();
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
        if (status == Status.DISABLED || !isDataConnected()) {
            return;
        }

        updateServerStatus(Status.CONNECTING);
        KafkaSender<MeasurementKey, SpecificRecord> sender = new RestSender<>(kafkaUrl, schemaRetriever, new SpecificRecordEncoder(false), new SpecificRecordEncoder(false));
        this.submitter = new KafkaDataSubmitter<>(this, sender, threadFactory);
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
        if (status == Status.DISABLED) {
            return;
        }
        updateServerStatus(Status.READY);
    }

    /**
     * Sends any remaining data and closes the tables and connections.
     * @throws IOException if the tables cannot be flushed
     */
    public void close() throws IOException {
        if (status != Status.DISABLED) {
            context.unregisterReceiver(connectivityReceiver);
        }
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
        for (DataCache<MeasurementKey, ? extends SpecificRecord> table : tables.values()) {
            table.close();
        }
    }

    @Override
    public void clean() {
        long timestamp = (System.currentTimeMillis() - dataRetention);
        for (DataCache<MeasurementKey, ? extends SpecificRecord> table : tables.values()) {
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
        if (status == Status.DISABLED) {
            return;
        }
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
    @SuppressWarnings("unchecked")
    @Override
    public <V extends SpecificRecord> MeasurementTable<V> getCache(AvroTopic<MeasurementKey, V> topic) {
        return (MeasurementTable<V>)this.tables.get(topic);
    }

    @Override
    public <V extends SpecificRecord> void addMeasurement(DataCache<MeasurementKey, V> table, MeasurementKey key, V value) {
        table.addMeasurement(key, value);
    }

    public Map<AvroTopic<MeasurementKey, ? extends SpecificRecord>, MeasurementTable<? extends SpecificRecord>> getCaches() {
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

    @Override
    public void updateRecordsSent(String topicName, int numberOfRecords) {
        synchronized (statusListeners) {
            for (ServerStatusListener listener : statusListeners) {
                listener.updateRecordsSent(topicName, numberOfRecords);
            }
            // Overwrite key-value if exists. Only stores the last
            this.lastNumberOfRecordsSent.put(topicName, numberOfRecords );
        }
    }

    public Map<String, Integer> getRecordsSent() {
        synchronized (statusListeners) {
            return this.lastNumberOfRecordsSent;
        }
    }

    public static class TableDataHandlerBuilder {
        private Context context;
        private int dbAgeMillis = 2500;
        private URL kafkaUrl;
        private SchemaRetriever schemaRetriever;
        private long dataRetentionMillis = 86400000;
        private AvroTopic<MeasurementKey, ? extends SpecificRecord>[] topics;
        private long kafkaUploadRate;
        private long kafkaCleanRate;
        private long kafkaRecordsSendLimit;
        private long senderConnectionTimeout;

        public TableDataHandlerBuilder setContext(Context context) {
            this.context = context;
            return this;
        }

        public TableDataHandlerBuilder setDbAgeMillis(int dbAgeMillis) {
            this.dbAgeMillis = dbAgeMillis;
            return this;
        }

        public TableDataHandlerBuilder setKafkaUrl(URL kafkaUrl) {
            this.kafkaUrl = kafkaUrl;
            return this;
        }

        public TableDataHandlerBuilder setSchemaRetriever(SchemaRetriever schemaRetriever) {
            this.schemaRetriever = schemaRetriever;
            return this;
        }

        public TableDataHandlerBuilder setDataRetentionMillis(long dataRetentionMillis) {
            this.dataRetentionMillis = dataRetentionMillis;
            return this;
        }

        public TableDataHandlerBuilder setTopics(AvroTopic<MeasurementKey, ? extends SpecificRecord>... topics) {
            this.topics = topics;
            return this;
        }

        public TableDataHandlerBuilder setKafkaUploadRate(long kafkaUploadRate) {
            this.kafkaUploadRate = kafkaUploadRate;
            return this;
        }

        public TableDataHandlerBuilder setKafkaCleanRate(long kafkaCleanRate) {
            this.kafkaCleanRate = kafkaCleanRate;
            return this;
        }

        public TableDataHandlerBuilder setKafkaRecordsSendLimit(long kafkaRecordsSendLimit) {
            this.kafkaRecordsSendLimit = kafkaRecordsSendLimit;
            return this;
        }

        public TableDataHandlerBuilder setSenderConnectionTimeout(long senderConnectionTimeout) {
            this.senderConnectionTimeout = senderConnectionTimeout;
            return this;
        }

        public TableDataHandler createTableDataHandler() {
            return new TableDataHandler(context, dbAgeMillis, kafkaUrl, schemaRetriever, dataRetentionMillis, kafkaUploadRate, kafkaCleanRate, kafkaRecordsSendLimit, senderConnectionTimeout, topics);
        }
    }
}
