package org.radarcns.android.data;

import org.radarcns.android.kafka.ServerStatusListener;
import org.radarcns.topic.AvroTopic;

import java.util.Map;

public interface DataHandler<K, V> extends ServerStatusListener {
    /**
     * Remove any old caches from the handler.
     */
    void clean();

    /** Get all caches */
    Map<AvroTopic<K, ? extends V>, ? extends DataCache<K, ? extends V>> getCaches();

    /** Get the cache for a given topic.
     * @return DataCache or null if the topic is not found.
     */
    <W extends V> DataCache<K, W> getCache(AvroTopic<K, W> topic);

    /**
     * Add a measurement using given cache.
     * @param cache cache to add measurement to.
     * @param key key of the measurement
     * @param value value of the measurement.
     */
    <W extends V> void addMeasurement(DataCache<K, W> cache, K key, W value);
}
