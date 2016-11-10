package org.radarcns.android;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;

public interface DeviceTopics {
    AvroTopic<MeasurementKey, ? extends SpecificRecord> getTopic(String name);
}
