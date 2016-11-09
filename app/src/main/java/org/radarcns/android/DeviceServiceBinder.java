package org.radarcns.android;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.data.Record;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.key.MeasurementKey;

import java.util.List;

public interface DeviceServiceBinder {
    /** Start scanning and recording from a compatible device. */
    DeviceState startRecording();
    /** Stop scanning and recording */
    void stopRecording();
    <V extends SpecificRecord> List<Record<MeasurementKey, V>> getRecords(AvroTopic<MeasurementKey, V> topic, int limit);
    /** Get the current device status */
    DeviceState getDeviceStatus();
    /** Get the current server status */
    ServerStatusListener.Status getServerStatus();
}
