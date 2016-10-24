package org.radarcns.kafka.rest;

import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.KafkaSender;
import org.radarcns.kafka.RecordList;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public class BatchedKafkaSender<K, V> implements KafkaSender<K, V> {
    private final KafkaSender<K, V> sender;
    private final int ageMillis;
    private final int maxBatchSize;
    private final Map<AvroTopic, RecordList<K, V>> cache;

    public BatchedKafkaSender(KafkaSender<K, V> sender, int ageMillis, int maxBatchSize) {
        this.sender = sender;
        this.ageMillis = ageMillis;
        this.maxBatchSize = maxBatchSize;
        this.cache = new HashMap<>();
    }

    @Override
    public void configure(Properties properties) {
        sender.configure(properties);
    }

    @Override
    public void send(AvroTopic topic, long offset, K key, V value) throws IOException {
        RecordList<K, V> batch;
        if (!this.isConnected()) {
            throw new IOException("Cannot send records to unconnected producer.");
        }
        synchronized (this) {
            batch = cache.get(topic);
            if (batch == null) {
                batch = new RecordList<>(topic);
                cache.put(topic, batch);
            }
            batch.add(offset, key, value);

            if (batch.size() >= maxBatchSize || System.currentTimeMillis() - batch.getFirstEntryTime() >= this.ageMillis) {
                cache.remove(topic);
            } else {
                batch = null;
            }
        }
        if (batch != null) {
            sender.send(batch);
        }
    }

    @Override
    public void send(RecordList<K, V> records) throws IOException {
        AvroTopic topic = records.getTopic();
        RecordList<K, V> batch;
        synchronized (this) {
            batch = cache.get(topic);
            if (batch != null) {
                batch.addAll(records.getRecords());
                if (batch.size() >= maxBatchSize || System.currentTimeMillis() - batch.getFirstEntryTime() >= this.ageMillis) {
                    cache.remove(topic);
                } else {
                    batch = null;
                }
            } else {
                if (records.size() >= maxBatchSize || System.currentTimeMillis() - records.getFirstEntryTime() >= this.ageMillis) {
                    batch = records;
                } else {
                    cache.put(topic, records);
                }
            }
        }
        if (batch != null) {
            sender.send(batch);
        }
    }

    @Override
    public long getLastSentOffset(AvroTopic topic) {
        return sender.getLastSentOffset(topic);
    }

    @Override
    public boolean isConnected() {
        return sender.isConnected();
    }

    @Override
    public boolean resetConnection() {
        return sender.resetConnection();
    }

    @Override
    public synchronized void clear() {
        cache.clear();
        sender.clear();
    }

    @Override
    public synchronized void flush() throws IOException {
        Iterator<RecordList<K, V>> batches = cache.values().iterator();
        while (batches.hasNext()) {
            sender.send(batches.next());
            batches.remove();
        }
        sender.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            flush();
        } finally {
            sender.close();
        }
    }
}
