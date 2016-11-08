package org.radarcns.data;

/**
 * A single data record.
 *
 * The time it gets created is stored as {@link #milliTimeAdded}.
 *
 * @param <K> key type
 * @param <V> value type
 */
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
