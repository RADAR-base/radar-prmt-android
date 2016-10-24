package org.radarcns.kafka;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class RecordList<K, V> implements Iterable<Record<K,V>> {
    private final List<Record<K, V>> records;
    private final AvroTopic topic;

    public RecordList(AvroTopic topic) {
        this.topic = topic;
        records = new ArrayList<>();
    }

    public void add(long offset, K key, V value) {
        records.add(new Record<>(offset, key, value));
    }

    public AvroTopic getTopic() {
        return topic;
    }

    public long getLastOffset() {
        return records.get(records.size() - 1).offset;
    }

    public Iterator<Record<K, V>> iterator() {
        return getRecords().iterator();
    }

    public long getFirstEntryTime() {
        if (isEmpty()) {
            throw new IllegalStateException("No first entry added yet.");
        }
        return records.get(0).milliTimeAdded;
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

    public void addAll(Collection<Record<K, V>> other) {
        this.records.addAll(other);
    }
}
