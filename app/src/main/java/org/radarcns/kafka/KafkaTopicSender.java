package org.radarcns.kafka;

import org.radarcns.data.Record;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public interface KafkaTopicSender<K, V> extends Closeable {
    /**
     * Send a message to Kafka eventually. Given offset must be strictly monotonically increasing
     * for subsequent calls.
     */
    void send(long offset, K key, V value) throws IOException;

    /**
     * Send a message to Kafka eventually.
     *
     * Contained offsets must be strictly monotonically increasing
     * for subsequent calls.
     */
    void send(List<Record<K, V>> records) throws IOException;

    /**
     * Get the latest offsets actually sent for a given topic. Returns -1L for unknown offsets.
     */
    long getLastSentOffset();

    /**
     * Clears any messages still in cache.
     */
    void clear();

    /**
     * Flush all remaining messages.
     */
    void flush() throws IOException;
}
