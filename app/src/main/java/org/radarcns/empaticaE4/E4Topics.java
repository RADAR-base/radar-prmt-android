package org.radarcns.empaticaE4;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;

/** Topic manager for topics concerning the Empatica E4. */
public class E4Topics {
    private final AvroTopic<MeasurementKey, EmpaticaE4Acceleration> accelerationTopic;
    private final AvroTopic<MeasurementKey, EmpaticaE4BatteryLevel> batteryLevelTopic;
    private final AvroTopic<MeasurementKey, EmpaticaE4BloodVolumePulse> bloodVolumePulseTopic;
    private final AvroTopic<MeasurementKey, EmpaticaE4ElectroDermalActivity> electroDermalActivityTopic;
    private final AvroTopic<MeasurementKey, EmpaticaE4InterBeatInterval> interBeatIntervalTopic;
    private final AvroTopic<MeasurementKey, EmpaticaE4Temperature> temperatureTopic;

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
        accelerationTopic = new AvroTopic<>("empatica_e4_acceleration", MeasurementKey.getClassSchema(), EmpaticaE4Acceleration.getClassSchema(), MeasurementKey.class, EmpaticaE4Acceleration.class);
        batteryLevelTopic = new AvroTopic<>("empatica_e4_battery_level", MeasurementKey.getClassSchema(), EmpaticaE4BatteryLevel.getClassSchema(), MeasurementKey.class, EmpaticaE4BatteryLevel.class);
        bloodVolumePulseTopic = new AvroTopic<>("empatica_e4_blood_volume_pulse", MeasurementKey.getClassSchema(), EmpaticaE4BloodVolumePulse.getClassSchema(), MeasurementKey.class, EmpaticaE4BloodVolumePulse.class);
        electroDermalActivityTopic = new AvroTopic<>("empatica_e4_electrodermal_activity", MeasurementKey.getClassSchema(), EmpaticaE4ElectroDermalActivity.getClassSchema(), MeasurementKey.class, EmpaticaE4ElectroDermalActivity.class);
        interBeatIntervalTopic = new AvroTopic<>("empatica_e4_inter_beat_interval", MeasurementKey.getClassSchema(), EmpaticaE4InterBeatInterval.getClassSchema(), MeasurementKey.class, EmpaticaE4InterBeatInterval.class);
        temperatureTopic = new AvroTopic<>("empatica_e4_temperature", MeasurementKey.getClassSchema(), EmpaticaE4Temperature.getClassSchema(), MeasurementKey.class, EmpaticaE4Temperature.class);
    }

    public AvroTopic<MeasurementKey, ? extends SpecificRecord> getTopic(String name) {
        switch (name) {
            case "empatica_e4_acceleration":
                return accelerationTopic;
            case "empatica_e4_battery_level":
                return batteryLevelTopic;
            case "empatica_e4_blood_volume_pulse":
                return bloodVolumePulseTopic;
            case "empatica_e4_electrodermal_activity":
                return electroDermalActivityTopic;
            case "empatica_e4_inter_beat_interval":
                return interBeatIntervalTopic;
            case "empatica_e4_temperature":
                return temperatureTopic;
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
}
