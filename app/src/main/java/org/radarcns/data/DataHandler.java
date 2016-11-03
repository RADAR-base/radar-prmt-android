package org.radarcns.data;

import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.rest.ServerStatusListener;

import java.util.Map;

public interface DataHandler<K, V> extends ServerStatusListener {
    /**
     * Remove any old caches from the handler.
     */
    void clean();
    Map<AvroTopic<K, ? extends V>, ? extends DataCache<K, ? extends V>> getCaches();
    <W extends V> DataCache<K, W> getCache(AvroTopic<K, W> topic);
    <W extends V> void addMeasurement(DataCache<K, W> cache, K key, W value);
}
