package org.radarcns.pebble2;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.android.DeviceTopics;
import org.radarcns.empaticaE4.EmpaticaE4Acceleration;
import org.radarcns.empaticaE4.EmpaticaE4BatteryLevel;
import org.radarcns.empaticaE4.EmpaticaE4BloodVolumePulse;
import org.radarcns.empaticaE4.EmpaticaE4ElectroDermalActivity;
import org.radarcns.empaticaE4.EmpaticaE4InterBeatInterval;
import org.radarcns.empaticaE4.EmpaticaE4SensorStatus;
import org.radarcns.empaticaE4.EmpaticaE4Temperature;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;

/** Topic manager for topics concerning the Empatica E4. */
public class Pebble2Topics implements DeviceTopics {
    private final AvroTopic<MeasurementKey, Pebble2Acceleration> accelerationTopic;
    private final AvroTopic<MeasurementKey, Pebble2BatteryLevel> batteryLevelTopic;
    private final AvroTopic<MeasurementKey, Pebble2HeartRate> heartRateTopic;
    private final AvroTopic<MeasurementKey, Pebble2HeartRateFiltered> heartRateFilteredTopic;

    private final static Object syncObject = new Object();
    private static Pebble2Topics instance = null;

    public static Pebble2Topics getInstance() {
        synchronized (syncObject) {
            if (instance == null) {
                instance = new Pebble2Topics();
            }
            return instance;
        }
    }

    private Pebble2Topics() {
        accelerationTopic = new AvroTopic<>("android_pebble2_acceleration", MeasurementKey.getClassSchema(), Pebble2Acceleration.getClassSchema(), MeasurementKey.class, Pebble2Acceleration.class);
        batteryLevelTopic = new AvroTopic<>("android_pebble2_battery_level", MeasurementKey.getClassSchema(), Pebble2BatteryLevel.getClassSchema(), MeasurementKey.class, Pebble2BatteryLevel.class);
        heartRateTopic = new AvroTopic<>("android_pebble2_heart_rate", MeasurementKey.getClassSchema(), Pebble2HeartRate.getClassSchema(), MeasurementKey.class, Pebble2HeartRate.class);
        heartRateFilteredTopic = new AvroTopic<>("android_pebble2_heart_rate_filtered", MeasurementKey.getClassSchema(), Pebble2HeartRateFiltered.getClassSchema(), MeasurementKey.class, Pebble2HeartRateFiltered.class);
    }

    @Override
    public AvroTopic<MeasurementKey, ? extends SpecificRecord> getTopic(String name) {
        switch (name) {
            case "android_pebble2_acceleration":
                return accelerationTopic;
            case "android_pebble2_battery_level":
                return batteryLevelTopic;
            case "android_pebble2_heart_rate":
                return heartRateTopic;
            case "android_pebble2_heart_rate_filtered":
                return heartRateFilteredTopic;
            default:
                throw new IllegalArgumentException("Topic " + name + " unknown");
        }
    }

    public AvroTopic<MeasurementKey, Pebble2Acceleration> getAccelerationTopic() {
        return accelerationTopic;
    }

    public AvroTopic<MeasurementKey, Pebble2BatteryLevel> getBatteryLevelTopic() {
        return batteryLevelTopic;
    }

    public AvroTopic<MeasurementKey, Pebble2HeartRate> getHeartRateTopic() {
        return heartRateTopic;
    }

    public AvroTopic<MeasurementKey, Pebble2HeartRateFiltered> getHeartRateFilteredTopic() {
        return heartRateFilteredTopic;
    }
}
