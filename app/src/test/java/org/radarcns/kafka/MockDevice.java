package org.radarcns.kafka;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.radarcns.empaticaE4.EmpaticaE4Acceleration;
import org.radarcns.empaticaE4.EmpaticaE4BatteryLevel;
import org.radarcns.empaticaE4.EmpaticaE4BloodVolumePulse;
import org.radarcns.empaticaE4.EmpaticaE4ElectroDermalActivity;
import org.radarcns.empaticaE4.EmpaticaE4InterBeatInterval;
import org.radarcns.empaticaE4.EmpaticaE4Tag;
import org.radarcns.empaticaE4.EmpaticaE4Temperature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class MockDevice<K> extends Thread {
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
    private final KafkaSender<K, SpecificRecord> sender;
    private final K deviceId;
    private final long nanoTimeStep;
    private final float batteryDecayFactor;
    private final float timeDriftFactor;
    private long lastSleep;
    private final static AtomicLong offset = new AtomicLong(0);
    private IOException exception;

    public MockDevice(KafkaSender<K, SpecificRecord> sender, K deviceId, Schema keySchema) {
        this.deviceId = deviceId;
        acceleration = new AvroTopic("mock_empatica_e4_acceleration", keySchema, EmpaticaE4Acceleration.getClassSchema());
        battery = new AvroTopic("mock_empatica_e4_battery_level", keySchema, EmpaticaE4BatteryLevel.getClassSchema());
        bvp = new AvroTopic("mock_empatica_e4_blood_volume_pulse", keySchema, EmpaticaE4BloodVolumePulse.getClassSchema());
        eda = new AvroTopic("mock_empatica_e4_electrodermal_activity", keySchema, EmpaticaE4ElectroDermalActivity.getClassSchema());
        ibi = new AvroTopic("mock_empatica_e4_inter_beat_interval", keySchema, EmpaticaE4InterBeatInterval.getClassSchema());
        tags = new AvroTopic("mock_empatica_e4_tag", keySchema, EmpaticaE4Tag.getClassSchema());
        temperature = new AvroTopic("mock_empatica_e4_temperature", keySchema, EmpaticaE4Temperature.getClassSchema());
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
        exception = null;
    }

    public void run() {
        lastSleep = System.nanoTime();

        try {
            for (int t = 0; t < Integer.MAX_VALUE; t++) {
                for (int i = 1; i <= hertz_modulus; i++) {
                    double tR = System.currentTimeMillis() / 1000d;
                    double tD = tR + t * timeDriftFactor;
                    sendIfNeeded(i, acceleration, new EmpaticaE4Acceleration(tD, tR, 15f, -15f, 64f));
                    sendIfNeeded(i, battery, new EmpaticaE4BatteryLevel(tD, tR, 1f - (batteryDecayFactor*t % 1)));
                    sendIfNeeded(i, bvp, new EmpaticaE4BloodVolumePulse(tD, tR, 80.0f));
                    sendIfNeeded(i, eda, new EmpaticaE4ElectroDermalActivity(tD, tR, 0.026897f));
                    sendIfNeeded(i, ibi, new EmpaticaE4InterBeatInterval(tD, tR, 0.921917f));
                    sendIfNeeded(i, tags, new EmpaticaE4Tag(tD, tR));
                    sendIfNeeded(i, temperature, new EmpaticaE4Temperature(tD, tR, 37.0f));
                    sleep();
                }
                logger.debug("Single time step {}", deviceId);
            }
        } catch (InterruptedException ex) {
            // do nothing, just exit the loop
        } catch (IOException e) {
            synchronized (this) {
                this.exception = e;
            }
            logger.error("MockDevice {} failed to send message", deviceId, e);
        }
    }

    private void sendIfNeeded(int timeStep, AvroTopic topic, SpecificRecord avroRecord) throws IOException {
        int hertz = topicFrequency.get(topic);
        if (hertz > 0 && timeStep % (hertz_modulus / hertz) == 0) {
            sender.send(topic, offset.incrementAndGet(), deviceId, avroRecord);
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

    public synchronized IOException getException() {
        return exception;
    }
}
