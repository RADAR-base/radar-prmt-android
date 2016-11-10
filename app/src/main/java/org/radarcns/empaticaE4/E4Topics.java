package org.radarcns.empaticaE4;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.android.DeviceTopics;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;

/** Topic manager for topics concerning the Empatica E4. */
public class E4Topics implements DeviceTopics {
    private final AvroTopic<MeasurementKey, EmpaticaE4Acceleration> accelerationTopic;
    private final AvroTopic<MeasurementKey, EmpaticaE4BatteryLevel> batteryLevelTopic;
    private final AvroTopic<MeasurementKey, EmpaticaE4BloodVolumePulse> bloodVolumePulseTopic;
    private final AvroTopic<MeasurementKey, EmpaticaE4ElectroDermalActivity> electroDermalActivityTopic;
    private final AvroTopic<MeasurementKey, EmpaticaE4InterBeatInterval> interBeatIntervalTopic;
    private final AvroTopic<MeasurementKey, EmpaticaE4Temperature> temperatureTopic;
    private final AvroTopic<MeasurementKey, EmpaticaE4SensorStatus> sensorStatusTopic;

    private final static Object syncObject = new Object();
    private static E4Topics instance = null;

    public static E4Topics getInstance() {
        synchronized (syncObject) {
            if (instance == null) {
                instance = new E4Topics();
            }
            return instance;
        }
    }

    private E4Topics() {
        accelerationTopic = new AvroTopic<>("android_empatica_e4_acceleration", MeasurementKey.getClassSchema(), EmpaticaE4Acceleration.getClassSchema(), MeasurementKey.class, EmpaticaE4Acceleration.class);
        batteryLevelTopic = new AvroTopic<>("android_empatica_e4_battery_level", MeasurementKey.getClassSchema(), EmpaticaE4BatteryLevel.getClassSchema(), MeasurementKey.class, EmpaticaE4BatteryLevel.class);
        bloodVolumePulseTopic = new AvroTopic<>("android_empatica_e4_blood_volume_pulse", MeasurementKey.getClassSchema(), EmpaticaE4BloodVolumePulse.getClassSchema(), MeasurementKey.class, EmpaticaE4BloodVolumePulse.class);
        electroDermalActivityTopic = new AvroTopic<>("android_empatica_e4_electrodermal_activity", MeasurementKey.getClassSchema(), EmpaticaE4ElectroDermalActivity.getClassSchema(), MeasurementKey.class, EmpaticaE4ElectroDermalActivity.class);
        interBeatIntervalTopic = new AvroTopic<>("android_empatica_e4_inter_beat_interval", MeasurementKey.getClassSchema(), EmpaticaE4InterBeatInterval.getClassSchema(), MeasurementKey.class, EmpaticaE4InterBeatInterval.class);
        temperatureTopic = new AvroTopic<>("android_empatica_e4_temperature", MeasurementKey.getClassSchema(), EmpaticaE4Temperature.getClassSchema(), MeasurementKey.class, EmpaticaE4Temperature.class);
        sensorStatusTopic = new AvroTopic<>("android_empatica_e4_sensor_status", MeasurementKey.getClassSchema(), EmpaticaE4SensorStatus.getClassSchema(), MeasurementKey.class, EmpaticaE4SensorStatus.class);
    }

    @Override
    public AvroTopic<MeasurementKey, ? extends SpecificRecord> getTopic(String name) {
        switch (name) {
            case "android_empatica_e4_acceleration":
                return accelerationTopic;
            case "android_empatica_e4_battery_level":
                return batteryLevelTopic;
            case "android_empatica_e4_blood_volume_pulse":
                return bloodVolumePulseTopic;
            case "android_empatica_e4_electrodermal_activity":
                return electroDermalActivityTopic;
            case "android_empatica_e4_inter_beat_interval":
                return interBeatIntervalTopic;
            case "android_empatica_e4_temperature":
                return temperatureTopic;
            case "android_empatica_e4_sensor_status":
                return sensorStatusTopic;
            default:
                throw new IllegalArgumentException("Topic " + name + " unknown");
        }
    }

    public AvroTopic<MeasurementKey, EmpaticaE4Acceleration> getAccelerationTopic() {
        return accelerationTopic;
    }

    public AvroTopic<MeasurementKey, EmpaticaE4BatteryLevel> getBatteryLevelTopic() {
        return batteryLevelTopic;
    }

    public AvroTopic<MeasurementKey, EmpaticaE4BloodVolumePulse> getBloodVolumePulseTopic() {
        return bloodVolumePulseTopic;
    }

    public AvroTopic<MeasurementKey, EmpaticaE4ElectroDermalActivity> getElectroDermalActivityTopic() {
        return electroDermalActivityTopic;
    }

    public AvroTopic<MeasurementKey, EmpaticaE4InterBeatInterval> getInterBeatIntervalTopic() {
        return interBeatIntervalTopic;
    }

    public AvroTopic<MeasurementKey, EmpaticaE4Temperature> getTemperatureTopic() {
        return temperatureTopic;
    }

    public AvroTopic<MeasurementKey, EmpaticaE4SensorStatus> getSensorStatusTopic() {
        return sensorStatusTopic;
    }
}
