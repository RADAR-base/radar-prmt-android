package org.radarcns.kafka;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.utils.SystemTime;
import org.radarcns.empatica.EmpaticaE4Acceleration;
import org.radarcns.empatica.EmpaticaE4BatteryLevel;
import org.radarcns.empatica.EmpaticaE4BloodVolumePulse;
import org.radarcns.empatica.EmpaticaE4ElectroDermalActivity;
import org.radarcns.empatica.EmpaticaE4InterBeatInterval;
import org.radarcns.empatica.EmpaticaE4Tag;
import org.radarcns.empatica.EmpaticaE4Temperature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.DoubleBuffer;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final AtomicLong offset = new AtomicLong(0);
    private IOException exception;

    //Frequency
    private int accelerationFrequency = 32;
    private int batteryFrequency = 1;
    private int bvpFrequency = 64;
    private int edaFrequency = 4;
    private int ibiFrequency = 1;
    private int tagsFrequency = 1;
    private int temperatureFrequency = 4;

    private final HashMap<String,Integer> counter;

    public MockDevice(KafkaSender<K, SpecificRecord> sender, K key, Schema keySchema, Class<K> keyClass) {
        this.key = key;
        acceleration = new AvroTopic<>("android_empatica_e4_acceleration", keySchema, EmpaticaE4Acceleration.getClassSchema(), keyClass, EmpaticaE4Acceleration.class);
        battery = new AvroTopic<>("android_empatica_e4_battery_level", keySchema, EmpaticaE4BatteryLevel.getClassSchema(), keyClass, EmpaticaE4BatteryLevel.class);
        bvp = new AvroTopic<>("android_empatica_e4_blood_volume_pulse", keySchema, EmpaticaE4BloodVolumePulse.getClassSchema(), keyClass, EmpaticaE4BloodVolumePulse.class);
        eda = new AvroTopic<>("android_empatica_e4_electrodermal_activity", keySchema, EmpaticaE4ElectroDermalActivity.getClassSchema(), keyClass, EmpaticaE4ElectroDermalActivity.class);
        ibi = new AvroTopic<>("android_empatica_e4_inter_beat_interval", keySchema, EmpaticaE4InterBeatInterval.getClassSchema(), keyClass, EmpaticaE4InterBeatInterval.class);
        tags = new AvroTopic<>("android_empatica_e4_tag", keySchema, EmpaticaE4Tag.getClassSchema(), keyClass, EmpaticaE4Tag.class);
        temperature = new AvroTopic<>("android_empatica_e4_temperature", keySchema, EmpaticaE4Temperature.getClassSchema(), keyClass, EmpaticaE4Temperature.class);
        hertz_modulus = 64;
        nanoTimeStep = 1000000000L / hertz_modulus;

        // decay
        Random random = new Random();
        batteryDecayFactor = 0.1f * random.nextFloat();
        timeDriftFactor = 0.01f * random.nextFloat();

        this.sender = sender;
        exception = null;

        counter = new HashMap<>();
    }

    public void run() {
        try {
            KafkaTopicSender<K, EmpaticaE4Acceleration> accelerationSender = sender.sender(acceleration);
            KafkaTopicSender<K, EmpaticaE4BatteryLevel> batterySender = sender.sender(battery);
            KafkaTopicSender<K, EmpaticaE4BloodVolumePulse> bvpSender = sender.sender(bvp);
            KafkaTopicSender<K, EmpaticaE4ElectroDermalActivity> edaSender = sender.sender(eda);
            KafkaTopicSender<K, EmpaticaE4InterBeatInterval> ibiSender = sender.sender(ibi);
            KafkaTopicSender<K, EmpaticaE4Tag> tagSender = sender.sender(tags);
            KafkaTopicSender<K, EmpaticaE4Temperature> temperatureSender = sender.sender(temperature);

            for (int t = 0; t < Integer.MAX_VALUE; t++) {
                for (int i = 0; i <= hertz_modulus; i++) {

                    long startTime = System.nanoTime();

                    double tR = System.currentTimeMillis() / 1000d;
                    double tD = tR + t * timeDriftFactor;
                    sendIfNeeded(i, accelerationFrequency, accelerationSender, new EmpaticaE4Acceleration(tD, tR, 15f, -15f, 64f));
                    sendIfNeeded(i, batteryFrequency, batterySender, new EmpaticaE4BatteryLevel(tD, tR, 1f - (batteryDecayFactor*t % 1)));
                    sendIfNeeded(i, bvpFrequency, bvpSender, new EmpaticaE4BloodVolumePulse(tD, tR, 80.0f));
                    sendIfNeeded(i, edaFrequency, edaSender, new EmpaticaE4ElectroDermalActivity(tD, tR, 0.026897f));
                    sendIfNeeded(i, ibiFrequency, ibiSender, new EmpaticaE4InterBeatInterval(tD, tR, 0.921917f));
                    //sendIfNeeded(i, tagsFrequency, tagSender, new EmpaticaE4Tag(tD, tR));
                    sendIfNeeded(i, temperatureFrequency, temperatureSender, new EmpaticaE4Temperature(tD, tR, 37.0f));

                    long endTime = System.nanoTime();

                    sleepMock(startTime, endTime);
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

        getResultOneMin();
    }

    private <V extends SpecificRecord> void sendIfNeeded(int timeStep, int frequency, KafkaTopicSender<K, V> topicSender, V avroRecord) throws IOException {
        if (frequency > 0 && timeStep % (hertz_modulus / frequency) == 0) {
            synchronized (offset) {
                topicSender.send(offset.incrementAndGet(), key, avroRecord);
                count(avroRecord);
            }
        }
    }

    private void sleepMock(long startTime, long endTime) throws InterruptedException {
        double time = 1000d - ((double)(endTime - startTime) / 1000000d);

        if(time > 0.0) {
            sleep((long) time);
        }
        else{
            logger.info("Time need to one complete send: {}msec",((double)(endTime - startTime) / 1000000d));
        }
    }

    private <V extends SpecificRecord> void count(V avroRecord){
        String sensorClass = avroRecord.getClass().getCanonicalName();
        Integer count = counter.get(sensorClass);
        counter.put(sensorClass, count == null ? 1 : count + 1);
    }

    public void getResultOneMin(){
        for (String sensor : counter.keySet()) {
            if(sensor.equals("org.radarcns.empatica.EmpaticaE4Temperature")){
                logger.info("Temperature: expected {} find {}. Loss-rate {}", temperatureFrequency,
                        (counter.get(sensor)), 1.0 - (counter.get(sensor).doubleValue())/(double)temperatureFrequency);
            }
            else if(sensor.equals("org.radarcns.empatica.EmpaticaE4BloodVolumePulse")){
                logger.info("BVP: expected {} find {}. Loss-rate {}", bvpFrequency,
                        (counter.get(sensor)), 1.0 - (counter.get(sensor).doubleValue())/(double)bvpFrequency);
            }
            else if(sensor.equals("org.radarcns.empatica.EmpaticaE4ElectroDermalActivity")){
                logger.info("EDA: expected {} find {}. Loss-rate {}", edaFrequency,
                        (counter.get(sensor)), 1.0 - (counter.get(sensor).doubleValue())/(double)edaFrequency);
            }
            else if(sensor.equals("org.radarcns.empatica.EmpaticaE4InterBeatInterval")){
                logger.info("IBI: expected {} find {}. Loss-rate {}", ibiFrequency,
                        (counter.get(sensor)), 1.0 - (counter.get(sensor).doubleValue())/(double)ibiFrequency);
            }
            else if(sensor.equals("org.radarcns.empatica.EmpaticaE4Acceleration")){
                logger.info("Acceleration: expected {} find {}. Loss-rate {}", accelerationFrequency,
                        (counter.get(sensor)), 1.0 - (counter.get(sensor).doubleValue())/(double)accelerationFrequency);
            }
            else if(sensor.equals("org.radarcns.empatica.EmpaticaE4BatteryLevel")){
                logger.info("Battery: expected {} find {}. Loss-rate {}", batteryFrequency,
                        (counter.get(sensor)), 1.0 - (counter.get(sensor).doubleValue())/(double)batteryFrequency);
            }
        }
    }

    public synchronized IOException getException() {
        return exception;
    }
}
