package org.radarcns.android.data;

import android.os.Parcel;
import android.util.Pair;

import org.radarcns.data.Record;
import org.radarcns.topic.AvroTopic;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.List;

public interface DataCache<K, V> extends Flushable, Closeable {
    /**
     * Get all unsent records in the cache.
     *
     * @return records.
     */
    List<Record<K, V>> unsentRecords(int limit) throws IOException;

    /**
     * Get latest records in the cache, from new to old.
     *
     * @return records.
     */
    List<Record<K, V>> getRecords(int limit) throws IOException;

    /**
     * Get a pair with the number of [unsent records], [sent records]
     */
    Pair<Long, Long> numberOfRecords();

    /**
     * Remove all records before a given offset.
     * @param offset offset (inclusive) to remove.
     * @return number of rows removed
     */
    int markSent(long offset) throws IOException;

    /** Add a new measurement to the cache. */
    void addMeasurement(K key, V value);

    /**
     * Remove all sent records before a given time.
     * @param millis time in milliseconds before which to remove.
     * @return number of rows removed
     */
    int removeBeforeTimestamp(long millis);

    /** Get the topic the cache stores. */
    AvroTopic<K, V> getTopic();

    /**
     * Write the latest records in the cache to a parcel, from new to old.
     */
    void writeRecordsToParcel(Parcel dest, int limit) throws IOException;

    /** Return a list to cache. It will be cleared immediately and should not be used again. */
    void returnList(List list);

    void setTimeWindow(long period);
}
