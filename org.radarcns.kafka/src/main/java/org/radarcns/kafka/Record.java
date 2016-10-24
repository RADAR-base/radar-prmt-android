package org.radarcns.kafka;

public class Record<K, V> {
    public final long offset;
    public final K key;
    public final V value;
    public final long milliTimeAdded;

    public Record(long offset, K key, V value) {
        this.offset = offset;
        this.key = key;
        this.value = value;
        this.milliTimeAdded = System.currentTimeMillis();
    }
}
