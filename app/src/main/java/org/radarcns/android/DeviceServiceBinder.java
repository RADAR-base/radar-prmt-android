package org.radarcns.android;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Pair;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.data.Record;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.key.MeasurementKey;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DeviceServiceBinder {
    /** Start scanning and recording from a compatible device.
     * @param acceptableIds a set of source IDs that may be connected to.
     *                      If empty, no selection is made.
     */
    DeviceState startRecording(@NonNull Set<String> acceptableIds);
    /** Stop scanning and recording */
    void stopRecording();
    <V extends SpecificRecord> List<Record<MeasurementKey, V>> getRecords(@NonNull AvroTopic<MeasurementKey, V> topic, int limit);
    /** Get the current device status */
    DeviceState getDeviceStatus();
    /** Get the current device name, or null if unknown. */
    String getDeviceName();
    /** Get the current server status */
    ServerStatusListener.Status getServerStatus();
    /** Get the last number of records sent */
    Map<String, Integer> getServerRecordsSent();
    /** Update the configuration of the service */
    void updateConfiguration(Bundle bundle);
    /** Number of records in cache [unsent] and [sent] */
    Pair<Long, Long> numberOfRecords();
}
