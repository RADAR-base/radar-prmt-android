package org.radarcns.data;

import android.os.Parcel;

import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;

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
    List<Record<K, V>> unsentRecords(int limit);

    /**
     * Get latest records in the cache, from new to old.
     *
     * @return records.
     */
    List<Record<K, V>> getRecords(int limit);

    /**
     * Remove all records before a given offset.
     * @param offset offset (inclusive) to remove.
     * @return number of rows removed
     */
    int markSent(long offset);

    void addMeasurement(K key, V value);

    /**
     * Remove all sent records before a given time.
     * @param millis time in milliseconds before which to remove.
     * @return number of rows removed
     */
    int removeBeforeTimestamp(long millis);

    AvroTopic<K, V> getTopic();

    /**
     * Write the latest records in the cache to a parcel, from new to old.
     */
    void writeRecordsToParcel(Parcel dest, int limit) throws IOException;
}
