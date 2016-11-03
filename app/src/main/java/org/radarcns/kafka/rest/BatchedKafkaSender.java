package org.radarcns.kafka.rest;

import org.radarcns.data.Record;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.KafkaSender;
import org.radarcns.kafka.KafkaTopicSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BatchedKafkaSender<K, V> implements KafkaSender<K, V> {
    private final KafkaSender<K, V> sender;
    private final int ageMillis;
    private final int maxBatchSize;

    public BatchedKafkaSender(KafkaSender<K, V> sender, int ageMillis, int maxBatchSize) {
        this.sender = sender;
        this.ageMillis = ageMillis;
        this.maxBatchSize = maxBatchSize;
    }

    @Override
    public <L extends K, W extends V> KafkaTopicSender<L, W> sender(final AvroTopic<L, W> topic) throws IOException {
        return new KafkaTopicSender<L, W>() {
            List<Record<L, W>> cache = new ArrayList<>();
            KafkaTopicSender<L, W> topicSender = sender.sender(topic);

            @Override
            public void send(long offset, L key, W value) throws IOException {
                if (!isConnected()) {
                    throw new IOException("Cannot send records to unconnected producer.");
                }
                cache.add(new Record<>(offset, key, value));

                if (exceedsBuffer(cache)) {
                    topicSender.send(cache);
                    cache.clear();
                }
            }

            @Override
            public void send(List<Record<L, W>> records) throws IOException {
                if (records.isEmpty()) {
                    return;
                }
                if (cache.isEmpty()) {
                    if (exceedsBuffer(records)) {
                        topicSender.send(records);
                    } else {
                        cache.addAll(records);
                    }
                } else {
                    cache.addAll(records);

                    if (exceedsBuffer(cache)) {
                        topicSender.send(cache);
                        cache.clear();
                    }
                }
            }

            @Override
            public long getLastSentOffset() {
                return topicSender.getLastSentOffset();
            }

            @Override
            public synchronized void clear() {
                cache.clear();
                topicSender.clear();
            }

            @Override
            public void flush() throws IOException {
                if (!cache.isEmpty()) {
                    topicSender.send(cache);
                    cache.clear();
                }
                topicSender.flush();
            }

            @Override
            public synchronized void close() throws IOException {
                try {
                    flush();
                } finally {
                    sender.close();
                }
            }

            private boolean exceedsBuffer(List<Record<L, W>> records) {
                return records.size() >= maxBatchSize || System.currentTimeMillis() - records.get(0).milliTimeAdded >= ageMillis;
            }
        };
    }

    @Override
    public boolean isConnected() {
        return sender.isConnected();
    }

    @Override
    public boolean resetConnection() {
        return sender.resetConnection();
    }

    @Override
    public synchronized void close() throws IOException {
        sender.close();
    }
}
