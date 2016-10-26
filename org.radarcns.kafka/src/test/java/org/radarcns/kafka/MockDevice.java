package org.radarcns.kafka;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class MockDevice extends Thread {
    private final static Logger logger = LoggerFactory.getLogger(MockDevice.class);

    private final AvroTopic acceleration;
    private final AvroTopic battery;
    private final AvroTopic bvp;
    private final AvroTopic eda;
    private final AvroTopic ibi;
    private final AvroTopic tags;
    private final AvroTopic temperature;
    private final int hertz_modulus;
    private final Map<AvroTopic, Integer> topicFrequency;
    private final KafkaSender<String, GenericRecord> sender;
    private final String deviceId;
    private final long nanoTimeStep;
    private final float batteryDecayFactor;
    private final float timeDriftFactor;
    private long lastSleep;
    private final static AtomicLong offset = new AtomicLong(0);

    public MockDevice(KafkaSender<String, GenericRecord> sender, String deviceId, SchemaRetriever schemaRetriever) {
        this.deviceId = deviceId;
        try {
            acceleration = new AvroTopic("empatica_e4_acceleration", schemaRetriever);
            battery = new AvroTopic("empatica_e4_battery_level", schemaRetriever);
            bvp = new AvroTopic("empatica_e4_blood_volume_pulse", schemaRetriever);
            eda = new AvroTopic("empatica_e4_electrodermal_activity", schemaRetriever);
            ibi = new AvroTopic("empatica_e4_inter_beat_interval", schemaRetriever);
            tags = new AvroTopic("empatica_e4_tag", schemaRetriever);
            temperature = new AvroTopic("empatica_e4_temperature", schemaRetriever);
        } catch (IOException ex) {
            logger.error("missing topic schema", ex);
            throw new RuntimeException(ex);
        }
        hertz_modulus = 64;
        nanoTimeStep = 1000000000L / hertz_modulus;
        lastSleep = 0;

        topicFrequency = new HashMap<>();
        topicFrequency.put(acceleration, 32); // 32
        topicFrequency.put(battery, 1);
        topicFrequency.put(bvp, 64); // 64
        topicFrequency.put(eda, 4); // 4
        topicFrequency.put(ibi, 1); // 1
        topicFrequency.put(tags, 1); // 1
        topicFrequency.put(temperature, 4); // 4

        // decay
        Random random = new Random();
        batteryDecayFactor = 0.1f * random.nextFloat();
        timeDriftFactor = 0.01f * random.nextFloat();

        this.sender = sender;
    }

    public void run() {
        lastSleep = System.nanoTime();
        Schema.Field x = acceleration.getValueField("x");
        Schema.Field y = acceleration.getValueField("y");
        Schema.Field z = acceleration.getValueField("z");
        Schema.Field batteryLevel = battery.getValueField("batteryLevel");
        Schema.Field bloodVolumePulse = bvp.getValueField("bloodVolumePulse");
        Schema.Field electroDermalActivity = eda.getValueField("electroDermalActivity");
        Schema.Field interBeatInterval = ibi.getValueField("interBeatInterval");
        Schema.Field temperatureField = temperature.getValueField("temperature");

        try {
            for (int t = 0; t < Integer.MAX_VALUE; t++) {
                for (int i = 1; i <= hertz_modulus; i++) {
                    sendIfNeeded(i, acceleration, x, 15f, y, -15f, z, 64f);
                    sendIfNeeded(i, battery, batteryLevel, 1f - (batteryDecayFactor*t % 1));
                    sendIfNeeded(i, bvp, bloodVolumePulse, 80.0f);
                    sendIfNeeded(i, eda, electroDermalActivity, 0.026897f);
                    sendIfNeeded(i, ibi, interBeatInterval, 0.921917f);
                    sendIfNeeded(i, tags);
                    sendIfNeeded(i, temperature, temperatureField, 37.0f);
                    sleep();
                }
                System.out.println("One time step");
            }
        } catch (InterruptedException ex) {
            // do nothing, just exit the loop
        }
    }

    private void sendIfNeeded(int timeStep, AvroTopic topic, Object... values) {
        int hertz = topicFrequency.get(topic);
        if (hertz > 0 && timeStep % (hertz_modulus / hertz) == 0) {
            GenericRecord avroRecord = topic.createValueRecord(System.currentTimeMillis() / 1000d + timeStep * timeDriftFactor, values);
            try {
                sender.send(topic, offset.incrementAndGet(), deviceId, avroRecord);
            } catch (IOException e) {
                System.err.println("Failed to send message to topic " + topic.getName());
                e.printStackTrace();
            }
        }
    }

    public void waitFor() throws InterruptedException {
        while (isAlive()) {
            join();
        }
    }

    private void sleep() throws InterruptedException {
        long currentTime = System.nanoTime();
        long nanoToSleep = nanoTimeStep - currentTime + lastSleep;
        if (nanoToSleep > 0) {
            Thread.sleep(nanoToSleep / 1000000L, ((int) nanoToSleep) % 1000000);
        }
        lastSleep = currentTime;
    }
}
