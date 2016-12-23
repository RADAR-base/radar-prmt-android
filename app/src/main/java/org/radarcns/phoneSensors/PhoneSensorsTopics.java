package org.radarcns.phoneSensors;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.android.DeviceTopics;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;

/** Topic manager for topics concerning the Empatica E4. */
public class PhoneSensorsTopics extends DeviceTopics {
    private final AvroTopic<MeasurementKey, PhoneSensorAcceleration> accelerationTopic;
    private final AvroTopic<MeasurementKey, PhoneSensorBatteryLevel> batteryLevelTopic;
    private final AvroTopic<MeasurementKey, PhoneSensorLight> lightTopic;
    private final AvroTopic<MeasurementKey, PhoneSensorCall> callTopic;

    private final static Object syncObject = new Object();
    private static PhoneSensorsTopics instance = null;

    public static PhoneSensorsTopics getInstance() {
        synchronized (syncObject) {
            if (instance == null) {
                instance = new PhoneSensorsTopics();
            }
            return instance;
        }
    }

    private PhoneSensorsTopics() {
        accelerationTopic = createTopic("android_phone_sensor_acceleration",
                PhoneSensorAcceleration.getClassSchema(),
                PhoneSensorAcceleration.class);
        batteryLevelTopic = createTopic("android_phone_sensor_battery_level",
                PhoneSensorBatteryLevel.getClassSchema(),
                PhoneSensorBatteryLevel.class);
        lightTopic = createTopic("android_phone_sensor_light",
                PhoneSensorLight.getClassSchema(),
                PhoneSensorLight.class);
        callTopic = createTopic("android_phone_sensor_call",
                PhoneSensorCall.getClassSchema(),
                PhoneSensorCall.class);
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

    public AvroTopic<MeasurementKey, PhoneSensorCall> getCallTopic() {
        return callTopic;
    }

}
