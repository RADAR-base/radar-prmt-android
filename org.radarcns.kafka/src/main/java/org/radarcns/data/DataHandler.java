package org.radarcns.data;

import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.rest.ServerStatusListener;

import java.util.Map;

public interface DataHandler<K, V> extends ServerStatusListener {
    /**
     * Remove any old caches from the handler.
     */
    void clean();
    Map<AvroTopic, ? extends DataCache<K, V>> getCaches();
    DataCache<K, V> getCache(AvroTopic topic);
}
