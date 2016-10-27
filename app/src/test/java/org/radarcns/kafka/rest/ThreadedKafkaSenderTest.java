package org.radarcns.kafka.rest;

import junit.framework.TestCase;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.radarcns.data.SpecificRecordEncoder;
import org.radarcns.data.StringEncoder;
import org.radarcns.kafka.MockDevice;
import org.radarcns.kafka.KafkaSender;
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

        SchemaRetriever schemaRetriever = new SchemaRetriever("http://radar-test.thehyve.net:8081");

        KafkaSender<String, SpecificRecord> directSender = new RestSender<>(new URL("http://radar-test.thehyve.net:8082"), schemaRetriever, new StringEncoder(), new SpecificRecordEncoder());
        KafkaSender<String, SpecificRecord> kafkaThread = new ThreadedKafkaSender<>(directSender);

        try (KafkaSender<String, SpecificRecord> sender = new BatchedKafkaSender<>(kafkaThread, 1000, 250)) {
            sender.configure(null);
            Schema stringSchema = Schema.create(Schema.Type.STRING);
            for (int i = 0; i < numberOfDevices; i++) {
                threads[i] = new MockDevice<>(sender, "device" + i, stringSchema);
                threads[i].start();
            }
            // stop running after 5 seconds, or after the first thread quits, whichever comes first
            threads[0].join(5_000L);
            for (MockDevice device : threads) {
                device.interrupt();
            }
            for (MockDevice device : threads) {
                device.join();
            }
            for (MockDevice device : threads) {
                assertNull("Device had IOException", device.getException());
            }
        }
    }
}
