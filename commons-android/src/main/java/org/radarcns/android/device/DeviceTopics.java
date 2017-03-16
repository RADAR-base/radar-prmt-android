package org.radarcns.android.device;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.radarcns.key.MeasurementKey;
import org.radarcns.topic.AvroTopic;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class DeviceTopics {
    private final Map<String, AvroTopic<MeasurementKey, ? extends SpecificRecord>> topicMap;

    protected DeviceTopics() {
        topicMap = new HashMap<>();
    }

    protected <W extends SpecificRecord> AvroTopic<MeasurementKey, W> createTopic(String name,
                                                        Schema valueSchema, Class<W> valueClass) {
        AvroTopic<MeasurementKey, W> topic = new AvroTopic<>(
                name, MeasurementKey.getClassSchema(), valueSchema,
                MeasurementKey.class, valueClass);
        topicMap.put(name, topic);
        return topic;
    }

    public AvroTopic<MeasurementKey, ? extends SpecificRecord> getTopic(String name) {
        return Objects.requireNonNull(topicMap.get(name), "Topic " + name + " unknown");
    }
}
