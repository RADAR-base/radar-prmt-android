package org.radarcns.empaticaE4;

import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;

import java.io.IOException;

/** Thread-safe topic manager for topics concerning the Empatica E4. */
public class E4Topics {
    private final AvroTopic accelerationTopic;
    private final AvroTopic batteryLevelTopic;
    private final AvroTopic bloodVolumePulseTopic;
    private final AvroTopic electroDermalActivityTopic;
    private final AvroTopic interBeatIntervalTopic;
    private final AvroTopic temperatureTopic;

    private final static Object syncObject = new Object();
    private static E4Topics instance = null;

    public static E4Topics getInstance() throws IOException {
        synchronized (syncObject) {
            if (instance == null) {
                instance = new E4Topics();
            }
            return instance;
        }
    }

    private E4Topics() throws IOException {
        accelerationTopic = new AvroTopic("empatica_e4_acceleration", MeasurementKey.getClassSchema(), EmpaticaE4Acceleration.getClassSchema());
        batteryLevelTopic = new AvroTopic("empatica_e4_battery_level", MeasurementKey.getClassSchema(), EmpaticaE4BatteryLevel.getClassSchema());
        bloodVolumePulseTopic = new AvroTopic("empatica_e4_blood_volume_pulse", MeasurementKey.getClassSchema(), EmpaticaE4BloodVolumePulse.getClassSchema());
        electroDermalActivityTopic = new AvroTopic("empatica_e4_electrodermal_activity", MeasurementKey.getClassSchema(), EmpaticaE4ElectroDermalActivity.getClassSchema());
        interBeatIntervalTopic = new AvroTopic("empatica_e4_inter_beat_interval", MeasurementKey.getClassSchema(), EmpaticaE4InterBeatInterval.getClassSchema());
        temperatureTopic = new AvroTopic("empatica_e4_temperature", MeasurementKey.getClassSchema(), EmpaticaE4Temperature.getClassSchema());
    }

    public AvroTopic getAccelerationTopic() {
        return accelerationTopic;
    }

    public AvroTopic getBatteryLevelTopic() {
        return batteryLevelTopic;
    }

    public AvroTopic getBloodVolumePulseTopic() {
        return bloodVolumePulseTopic;
    }

    public AvroTopic getElectroDermalActivityTopic() {
        return electroDermalActivityTopic;
    }

    public AvroTopic getInterBeatIntervalTopic() {
        return interBeatIntervalTopic;
    }

    public AvroTopic getTemperatureTopic() {
        return temperatureTopic;
    }
}
