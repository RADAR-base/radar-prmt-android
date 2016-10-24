package org.radarcns.kafka.rest;

import junit.framework.TestCase;

import org.apache.avro.generic.GenericRecord;
import org.radarcns.kafka.MockDevice;
import org.radarcns.kafka.KafkaSender;
import org.radarcns.kafka.LocalSchemaRetriever;
import org.radarcns.kafka.SchemaRegistryRetriever;
import org.radarcns.kafka.SchemaRetriever;

import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadedKafkaSenderTest extends TestCase {
    private Logger logger = LoggerFactory.getLogger(ThreadedKafkaSenderTest.class);

    public void testMain() throws Exception {
        System.out.println(System.currentTimeMillis());
        int numberOfDevices = 50;

        logger.info("Simulating the load of " + numberOfDevices);
        MockDevice[] threads = new MockDevice[numberOfDevices];
        KafkaSender[] senders = new KafkaSender[1];
        SchemaRetriever schemaRetriever = new SchemaRegistryRetriever("http://radar-test.thehyve.net:8081");
        SchemaRetriever localSchemaRetriever =  new LocalSchemaRetriever();

        KafkaSender<String, GenericRecord> kafkaThread = new ThreadedKafkaSender<>(new RestSender<>(new URL("http://radar-test.thehyve.net:8082"), schemaRetriever, new StringEncoder(), new GenericRecordEncoder()));
        senders[0] = new BatchedKafkaSender<>(kafkaThread, 1000, 250);
        senders[0].configure(null);
        for (int i = 0; i < numberOfDevices; i++) {
            threads[i] = new MockDevice(senders[0], "device" + i, localSchemaRetriever);
            threads[i].start();
        }
        Thread.sleep(5_000L);
        for (MockDevice device : threads) {
            device.interrupt();
            device.waitFor();
        }
        for (KafkaSender sender : senders) {
            sender.close();
        }
    }
}