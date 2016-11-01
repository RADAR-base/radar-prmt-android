package org.radarcns.data;

import org.radarcns.kafka.AvroTopic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class RecordList<K, V> implements Iterable<Record<K,V>> {
    private final List<Record<K, V>> records;
    private final AvroTopic<K, V> topic;

    public RecordList(AvroTopic topic) {
        this.topic = topic;
        records = new ArrayList<>();
    }
    public RecordList(AvroTopic topic, int initialCapacity) {
        this.topic = topic;
        records = new ArrayList<>(initialCapacity);
    }

    public void add(long offset, K key, V value) {
        records.add(new Record<>(offset, key, value));
    }

    public AvroTopic<K, V> getTopic() {
        return topic;
    }

    public long getLastOffset() {
        return records.get(records.size() - 1).offset;
    }

    public Iterator<Record<K, V>> iterator() {
        return getRecords().iterator();
    }

    public List<Record<K, V>> getRecords() {
        return Collections.unmodifiableList(records);
    }

    public int size() {
        return records.size();
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }
}
