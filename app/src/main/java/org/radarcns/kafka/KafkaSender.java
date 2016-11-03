package org.radarcns.kafka;

import java.io.Closeable;
import java.io.IOException;

/** Thread-safe sender */
public interface KafkaSender<K, V> extends Closeable {
    /** Get a non thread-safe sender instance. */
    <L extends K, W extends V> KafkaTopicSender<L, W> sender(AvroTopic<L, W> topic) throws IOException;

    /**
     * If the sender is no longer connected, try to reconnect.
     * @return whether the connection has been restored.
     */
    boolean resetConnection();

    /**
     * Whether the sender is connected to the Kafka system.
     */
    boolean isConnected();
}
