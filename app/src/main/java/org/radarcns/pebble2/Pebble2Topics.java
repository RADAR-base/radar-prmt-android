package org.radarcns.pebble2;

import org.radarcns.android.DeviceTopics;
import org.radarcns.key.MeasurementKey;
import org.radarcns.pebble.Pebble2Acceleration;
import org.radarcns.pebble.Pebble2BatteryLevel;
import org.radarcns.pebble.Pebble2HeartRate;
import org.radarcns.pebble.Pebble2HeartRateFiltered;
import org.radarcns.topic.AvroTopic;

/** Topic manager for topics concerning the Empatica E4. */
public class Pebble2Topics extends DeviceTopics {
    private final AvroTopic<MeasurementKey, Pebble2Acceleration> accelerationTopic;
    private final AvroTopic<MeasurementKey, Pebble2BatteryLevel> batteryLevelTopic;
    private final AvroTopic<MeasurementKey, Pebble2HeartRate> heartRateTopic;
    private final AvroTopic<MeasurementKey, Pebble2HeartRateFiltered> heartRateFilteredTopic;

    private static final Object syncObject = new Object();
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
        accelerationTopic = createTopic("android_pebble2_acceleration",
                Pebble2Acceleration.getClassSchema(),
                Pebble2Acceleration.class);
        batteryLevelTopic = createTopic("android_pebble2_battery_level",
                Pebble2BatteryLevel.getClassSchema(),
                Pebble2BatteryLevel.class);
        heartRateTopic = createTopic("android_pebble2_heart_rate",
                Pebble2HeartRate.getClassSchema(),
                Pebble2HeartRate.class);
        heartRateFilteredTopic = createTopic("android_pebble2_heart_rate_filtered",
                Pebble2HeartRateFiltered.getClassSchema(),
                Pebble2HeartRateFiltered.class);
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
