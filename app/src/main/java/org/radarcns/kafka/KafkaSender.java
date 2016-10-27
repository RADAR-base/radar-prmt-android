package org.radarcns.kafka;

import org.radarcns.data.RecordList;

import java.io.Closeable;
import java.io.IOException;
import java.util.Properties;

public interface KafkaSender<K, V> extends Closeable {
    /**
     * Configure any properties. Must be called before anything else.
     */
    void configure(Properties properties);

    /**
     * Send a message to Kafka eventually. Given offset must be strictly monotonically increasing
     * for subsequent calls.
     */
    void send(AvroTopic topic, long offset, K key, V value) throws IOException;

    /**
     * Send a message to Kafka eventually.
     *
     * Contained offsets must be strictly monotonically increasing
     * for subsequent calls.
     */
    void send(RecordList<K, V> records) throws IOException;

    /**
     * Get the latest offsets actually sent for a given topic. Returns -1L for unknown offsets.
     */
    long getLastSentOffset(AvroTopic topic);

    /**
     * If the sender is no longer connected, try to reconnect.
     * @return whether the connection has been restored.
     */
    boolean resetConnection();

    /**
     * Whether the sender is connected to the Kafka system.
     */
    boolean isConnected();

    /**
     * Clears any messages still in cache.
     */
    void clear();

    /**
     * Flush all remaining messages.
     */
    void flush() throws IOException;

    /**
     * Close the connection.
     */
    void close() throws IOException;
}
