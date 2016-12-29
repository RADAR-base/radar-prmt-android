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
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class MockDevice<K> extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(MockDevice.class);

    private final AvroTopic<K, EmpaticaE4Acceleration> acceleration;
    private final AvroTopic<K, EmpaticaE4BatteryLevel> battery;
    private final AvroTopic<K, EmpaticaE4BloodVolumePulse> bvp;
    private final AvroTopic<K, EmpaticaE4ElectroDermalActivity> eda;
    private final AvroTopic<K, EmpaticaE4InterBeatInterval> ibi;
    private final AvroTopic<K, EmpaticaE4Tag> tags;
    private final AvroTopic<K, EmpaticaE4Temperature> temperature;
    private final int hertz_modulus;
    private final KafkaSender<K, SpecificRecord> sender;
    private final K key;
    private final long nanoTimeStep;
    private final float batteryDecayFactor;
    private final float timeDriftFactor;
    private long lastSleep;
    private static final AtomicLong offset = new AtomicLong(0);
    private IOException exception;

    public MockDevice(KafkaSender<K, SpecificRecord> sender, K key, Schema keySchema, Class<K> keyClass) {
        this.key = key;
        acceleration = new AvroTopic<>("mock_empatica_e4_acceleration", keySchema, EmpaticaE4Acceleration.getClassSchema(), keyClass, EmpaticaE4Acceleration.class);
        battery = new AvroTopic<>("mock_empatica_e4_battery_level", keySchema, EmpaticaE4BatteryLevel.getClassSchema(), keyClass, EmpaticaE4BatteryLevel.class);
        bvp = new AvroTopic<>("mock_empatica_e4_blood_volume_pulse", keySchema, EmpaticaE4BloodVolumePulse.getClassSchema(), keyClass, EmpaticaE4BloodVolumePulse.class);
        eda = new AvroTopic<>("mock_empatica_e4_electrodermal_activity", keySchema, EmpaticaE4ElectroDermalActivity.getClassSchema(), keyClass, EmpaticaE4ElectroDermalActivity.class);
        ibi = new AvroTopic<>("mock_empatica_e4_inter_beat_interval", keySchema, EmpaticaE4InterBeatInterval.getClassSchema(), keyClass, EmpaticaE4InterBeatInterval.class);
        tags = new AvroTopic<>("mock_empatica_e4_tag", keySchema, EmpaticaE4Tag.getClassSchema(), keyClass, EmpaticaE4Tag.class);
        temperature = new AvroTopic<>("mock_empatica_e4_temperature", keySchema, EmpaticaE4Temperature.getClassSchema(), keyClass, EmpaticaE4Temperature.class);
        hertz_modulus = 64;
        nanoTimeStep = 1000000000L / hertz_modulus;
        lastSleep = 0;


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
            KafkaTopicSender<K, EmpaticaE4Acceleration> accelerationSender = sender.sender(acceleration);
            KafkaTopicSender<K, EmpaticaE4BatteryLevel> batterySender = sender.sender(battery);
            KafkaTopicSender<K, EmpaticaE4BloodVolumePulse> bvpSender = sender.sender(bvp);
            KafkaTopicSender<K, EmpaticaE4ElectroDermalActivity> edaSender = sender.sender(eda);
            KafkaTopicSender<K, EmpaticaE4InterBeatInterval> ibiSender = sender.sender(ibi);
            KafkaTopicSender<K, EmpaticaE4Tag> tagSender = sender.sender(tags);
            KafkaTopicSender<K, EmpaticaE4Temperature> temperatureSender = sender.sender(temperature);

            int accelerationFrequency = 32;
            int batteryFrequency = 1;
            int bvpFrequency = 64;
            int edaFrequency = 4;
            int ibiFrequency = 1;
            int tagsFrequency = 1;
            int temperatureFrequency = 4;

            for (int t = 0; t < Integer.MAX_VALUE; t++) {
                for (int i = 1; i <= hertz_modulus; i++) {
                    double tR = System.currentTimeMillis() / 1000d;
                    double tD = tR + t * timeDriftFactor;
                    sendIfNeeded(i, accelerationFrequency, accelerationSender, new EmpaticaE4Acceleration(tD, tR, 15f, -15f, 64f));
                    sendIfNeeded(i, batteryFrequency, batterySender, new EmpaticaE4BatteryLevel(tD, tR, 1f - (batteryDecayFactor*t % 1)));
                    sendIfNeeded(i, bvpFrequency, bvpSender, new EmpaticaE4BloodVolumePulse(tD, tR, 80.0f));
                    sendIfNeeded(i, edaFrequency, edaSender, new EmpaticaE4ElectroDermalActivity(tD, tR, 0.026897f));
                    sendIfNeeded(i, ibiFrequency, ibiSender, new EmpaticaE4InterBeatInterval(tD, tR, 0.921917f));
                    sendIfNeeded(i, tagsFrequency, tagSender, new EmpaticaE4Tag(tD, tR));
                    sendIfNeeded(i, temperatureFrequency, temperatureSender, new EmpaticaE4Temperature(tD, tR, 37.0f));
                    sleep();
                }
                logger.debug("Single time step {}", key);
            }
        } catch (InterruptedException ex) {
            // do nothing, just exit the loop
        } catch (IOException e) {
            synchronized (this) {
                this.exception = e;
            }
            logger.error("MockDevice {} failed to send message", key, e);
        }
    }

    private <V extends SpecificRecord> void sendIfNeeded(int timeStep, int frequency, KafkaTopicSender<K, V> topicSender, V avroRecord) throws IOException {
        if (frequency > 0 && timeStep % (hertz_modulus / frequency) == 0) {
            synchronized (offset) {
                topicSender.send(offset.incrementAndGet(), key, avroRecord);
            }
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
