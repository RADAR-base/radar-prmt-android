package org.radarcns.empaticaE4;

import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.LocalSchemaRetriever;
import org.radarcns.kafka.SchemaRetriever;

import java.io.IOException;

/** Thread-safe topic manager for topics concerning the Empatica E4. */
public class E4Topics {
    private final ThreadLocal<AvroTopic> accelerationTopic;
    private final ThreadLocal<AvroTopic> batteryLevelTopic;
    private final ThreadLocal<AvroTopic> bloodVolumePulseTopic;
    private final ThreadLocal<AvroTopic> electroDermalActivityTopic;
    private final ThreadLocal<AvroTopic> interBeatIntervalTopic;
    private final ThreadLocal<AvroTopic> temperatureTopic;

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
        SchemaRetriever localSchemaRetriever = new LocalSchemaRetriever();
        accelerationTopic = AvroTopic.newThreadLocalTopic("empatica_e4_acceleration", localSchemaRetriever);
        batteryLevelTopic = AvroTopic.newThreadLocalTopic("empatica_e4_battery_level", localSchemaRetriever);
        bloodVolumePulseTopic = AvroTopic.newThreadLocalTopic("empatica_e4_blood_volume_pulse", localSchemaRetriever);
        electroDermalActivityTopic = AvroTopic.newThreadLocalTopic("empatica_e4_electrodermal_activity", localSchemaRetriever);
        interBeatIntervalTopic = AvroTopic.newThreadLocalTopic("empatica_e4_inter_beat_interval", localSchemaRetriever);
        temperatureTopic = AvroTopic.newThreadLocalTopic("empatica_e4_temperature", localSchemaRetriever);
    }

    public AvroTopic getAccelerationTopic() {
        return accelerationTopic.get();
    }

    public AvroTopic getBatteryLevelTopic() {
        return batteryLevelTopic.get();
    }

    public AvroTopic getBloodVolumePulseTopic() {
        return bloodVolumePulseTopic.get();
    }

    public AvroTopic getElectroDermalActivityTopic() {
        return electroDermalActivityTopic.get();
    }

    public AvroTopic getInterBeatIntervalTopic() {
        return interBeatIntervalTopic.get();
    }

    public AvroTopic getTemperatureTopic() {
        return temperatureTopic.get();
    }
}
