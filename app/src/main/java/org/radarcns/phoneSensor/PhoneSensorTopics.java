package org.radarcns.phoneSensor;

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
public class PhoneSensorTopics implements DeviceTopics {
    private final AvroTopic<MeasurementKey, PhoneSensorAcceleration> accelerationTopic;
    private final AvroTopic<MeasurementKey, PhoneSensorBatteryLevel> batteryLevelTopic;
    private final AvroTopic<MeasurementKey, PhoneSensorLight> lightTopic;

    private final static Object syncObject = new Object();
    private static PhoneSensorTopics instance = null;

    public static PhoneSensorTopics getInstance() {
        synchronized (syncObject) {
            if (instance == null) {
                instance = new PhoneSensorTopics();
            }
            return instance;
        }
    }

    private PhoneSensorTopics() {
        accelerationTopic = new AvroTopic<>("android_phone_sensor_acceleration", MeasurementKey.getClassSchema(), PhoneSensorAcceleration.getClassSchema(), MeasurementKey.class, PhoneSensorAcceleration.class);
        batteryLevelTopic = new AvroTopic<>("android_phone_sensor_battery_level", MeasurementKey.getClassSchema(), PhoneSensorBatteryLevel.getClassSchema(), MeasurementKey.class, PhoneSensorBatteryLevel.class);
        lightTopic = new AvroTopic<>("android_phone_sensor_light", MeasurementKey.getClassSchema(), PhoneSensorLight.getClassSchema(), MeasurementKey.class, PhoneSensorLight.class);
    }

    @Override
    public AvroTopic<MeasurementKey, ? extends SpecificRecord> getTopic(String name) {
        switch (name) {
            case "android_phone_sensor_acceleration":
                return accelerationTopic;
            case "android_phone_sensor_battery_level":
                return batteryLevelTopic;
            case "android_phone_sensor_light":
                return lightTopic;
            default:
                throw new IllegalArgumentException("Topic " + name + " unknown");
        }
    }

    public AvroTopic<MeasurementKey, PhoneSensorAcceleration> getAccelerationTopic() {
        return accelerationTopic;
    }

    public AvroTopic<MeasurementKey, PhoneSensorBatteryLevel> getBatteryLevelTopic() {
        return batteryLevelTopic;
    }

    public AvroTopic<MeasurementKey, PhoneSensorLight> getLightTopic() {
        return lightTopic;
    }

}
