package org.radarcns.phone;

import org.radarcns.android.DeviceTopics;
import org.radarcns.key.MeasurementKey;
import org.radarcns.topic.AvroTopic;

/** Topic manager for topics concerning the Phone sensors. */
public class PhoneTopics extends DeviceTopics {
    private final AvroTopic<MeasurementKey, PhoneAcceleration> accelerationTopic;
    private final AvroTopic<MeasurementKey, PhoneBatteryLevel> batteryLevelTopic;
    private final AvroTopic<MeasurementKey, PhoneLight> lightTopic;

    private static final Object syncObject = new Object();
    private static PhoneTopics instance = null;

    public static PhoneTopics getInstance() {
        synchronized (syncObject) {
            if (instance == null) {
                instance = new PhoneTopics();
            }
            return instance;
        }
    }

    private PhoneTopics() {
        accelerationTopic = createTopic("android_phone_acceleration",
                PhoneAcceleration.getClassSchema(),
                PhoneAcceleration.class);
        batteryLevelTopic = createTopic("android_phone_battery_level",
                PhoneBatteryLevel.getClassSchema(),
                PhoneBatteryLevel.class);
        lightTopic = createTopic("android_phone_light",
                PhoneLight.getClassSchema(),
                PhoneLight.class);
    }

    public AvroTopic<MeasurementKey, PhoneAcceleration> getAccelerationTopic() {
        return accelerationTopic;
    }

    public AvroTopic<MeasurementKey, PhoneBatteryLevel> getBatteryLevelTopic() {
        return batteryLevelTopic;
    }

    public AvroTopic<MeasurementKey, PhoneLight> getLightTopic() {
        return lightTopic;
    }

}
