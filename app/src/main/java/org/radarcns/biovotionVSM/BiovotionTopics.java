package org.radarcns.biovotionVSM;

import org.radarcns.android.DeviceTopics;
import org.radarcns.biovotion.BiovotionVSMBatteryState;
import org.radarcns.biovotion.BiovotionVSMBloodPulseWave;
import org.radarcns.biovotion.BiovotionVSMHeartRate;
import org.radarcns.biovotion.BiovotionVSMSpO2;
import org.radarcns.biovotion.BiovotionVSMTemperature;
import org.radarcns.topic.AvroTopic;
import org.radarcns.key.MeasurementKey;

/** Topic manager for topics concerning the Biovotion VSM. */
public class BiovotionTopics extends DeviceTopics {
    private final AvroTopic<MeasurementKey, BiovotionVSMBatteryState> batteryStateTopic;
    private final AvroTopic<MeasurementKey, BiovotionVSMBloodPulseWave> bloodPulseWaveTopic;
    private final AvroTopic<MeasurementKey, BiovotionVSMSpO2> spo2Topic;
    private final AvroTopic<MeasurementKey, BiovotionVSMHeartRate> heartRateTopic;
    private final AvroTopic<MeasurementKey, BiovotionVSMTemperature> temperatureTopic;

    private static final Object syncObject = new Object();
    private static BiovotionTopics instance = null;

    public static BiovotionTopics getInstance() {
        synchronized (syncObject) {
            if (instance == null) {
                instance = new BiovotionTopics();
            }
            return instance;
        }
    }

    private BiovotionTopics() {
        batteryStateTopic = createTopic("android_biovotion_battery_state",
                BiovotionVSMBatteryState.getClassSchema(),
                BiovotionVSMBatteryState.class);
        bloodPulseWaveTopic = createTopic("android_biovotion_blood_pulse_wave",
                BiovotionVSMBloodPulseWave.getClassSchema(),
                BiovotionVSMBloodPulseWave.class);
        spo2Topic = createTopic("android_biovotion_spo2",
                BiovotionVSMSpO2.getClassSchema(),
                BiovotionVSMSpO2.class);
        heartRateTopic = createTopic("android_biovotion_heart_rate",
                BiovotionVSMHeartRate.getClassSchema(),
                BiovotionVSMHeartRate.class);
        temperatureTopic = createTopic("android_biovotion_temperature",
                BiovotionVSMTemperature.getClassSchema(),
                BiovotionVSMTemperature.class);
    }

    public AvroTopic<MeasurementKey, BiovotionVSMBatteryState> getBatteryStateTopic() {
        return batteryStateTopic;
    }

    public AvroTopic<MeasurementKey, BiovotionVSMBloodPulseWave> getBloodPulseWaveTopic() {
        return bloodPulseWaveTopic;
    }

    public AvroTopic<MeasurementKey, BiovotionVSMSpO2> getSpO2Topic() {
        return spo2Topic;
    }

    public AvroTopic<MeasurementKey, BiovotionVSMHeartRate> getHeartRateTopic() {
        return heartRateTopic;
    }

    public AvroTopic<MeasurementKey, BiovotionVSMTemperature> getTemperatureTopic() {
        return temperatureTopic;
    }
}
