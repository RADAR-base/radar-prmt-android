package org.radarcns.data;

import org.radarcns.kafka.AvroTopic;

import java.io.Closeable;
import java.io.Flushable;

public interface DataCache<K, V> extends Flushable, Closeable {
    /**
     * Get all unsent records in the cache.
     *
     * Use in a try-with-resources statement.
     * @return Iterator records.
     */
    RecordIterable<K, V> unsentRecords(int limit);

    /**
     * Remove all records before a given offset.
     * @param offset offset (inclusive) to remove.
     * @return number of rows removed
     */
    int markSent(long offset);

    /**
     * Remove all sent records before a given time.
     * @param millis time in milliseconds before which to remove.
     * @return number of rows removed
     */
    int removeBeforeTimestamp(long millis);


    AvroTopic getTopic();
}
