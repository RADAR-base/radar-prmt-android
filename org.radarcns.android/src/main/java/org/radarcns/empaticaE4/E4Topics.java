package org.radarcns.empaticaE4;

import org.radarcns.SchemaRetriever;
import org.radarcns.collect.LocalSchemaRetriever;
import org.radarcns.collect.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** Thread-safe topic manager for topics concerning the Empatica E4. */
public class E4Topics {
    private final ThreadLocal<Topic> accelerationTopic;
    private final ThreadLocal<Topic> batteryLevelTopic;
    private final ThreadLocal<Topic> bloodVolumePulseTopic;
    private final ThreadLocal<Topic> electroDermalActivityTopic;
    private final ThreadLocal<Topic> interBeatIntervalTopic;
    private final ThreadLocal<Topic> temperatureTopic;

    private final static Object syncObject = new Object();
    private static E4Topics instance = null;
    private final static Logger logger = LoggerFactory.getLogger(E4Topics.class);

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
        accelerationTopic = new ThreadLocalTopic("empatica_e4_acceleration", localSchemaRetriever);
        batteryLevelTopic = new ThreadLocalTopic("empatica_e4_battery_level", localSchemaRetriever);
        bloodVolumePulseTopic = new ThreadLocalTopic("empatica_e4_blood_volume_pulse", localSchemaRetriever);
        electroDermalActivityTopic = new ThreadLocalTopic("empatica_e4_electrodermal_activity", localSchemaRetriever);
        interBeatIntervalTopic = new ThreadLocalTopic("empatica_e4_inter_beat_interval", localSchemaRetriever);
        temperatureTopic = new ThreadLocalTopic("empatica_e4_temperature", localSchemaRetriever);
        if (accelerationTopic.get() == null ||
                batteryLevelTopic.get() == null ||
                bloodVolumePulseTopic.get() == null ||
                electroDermalActivityTopic.get() == null ||
                interBeatIntervalTopic.get() == null ||
                temperatureTopic.get() == null) {
            throw new IOException("Topic cannot be retrieved.");
        }
    }

    private class ThreadLocalTopic extends ThreadLocal<Topic> {
        final String name;
        final SchemaRetriever schemaRetriever;
        ThreadLocalTopic(String name, SchemaRetriever retriever) {
            this.name = name;
            this.schemaRetriever = retriever;
        }
        @Override
        protected Topic initialValue() {
            try {
                return new Topic(name, schemaRetriever);
            } catch (IOException e) {
                logger.error("Topic {} cannot be retrieved", name, e);
                return null;
            }
        }
    }

    public Topic getAccelerationTopic() {
        return accelerationTopic.get();
    }

    public Topic getBatteryLevelTopic() {
        return batteryLevelTopic.get();
    }

    public Topic getBloodVolumePulseTopic() {
        return bloodVolumePulseTopic.get();
    }

    public Topic getElectroDermalActivityTopic() {
        return electroDermalActivityTopic.get();
    }

    public Topic getInterBeatIntervalTopic() {
        return interBeatIntervalTopic.get();
    }

    public Topic getTemperatureTopic() {
        return temperatureTopic.get();
    }
}
