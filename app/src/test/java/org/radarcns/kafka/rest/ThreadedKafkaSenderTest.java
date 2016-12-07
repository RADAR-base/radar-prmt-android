package org.radarcns.kafka.rest;

import junit.framework.TestCase;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.radarcns.data.AvroEncoder;
import org.radarcns.data.SpecificRecordEncoder;
import org.radarcns.data.StringEncoder;
import org.radarcns.kafka.MockDevice;
import org.radarcns.kafka.KafkaSender;
import org.radarcns.kafka.SchemaRetriever;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadedKafkaSenderTest extends TestCase {
    private Logger logger = LoggerFactory.getLogger(ThreadedKafkaSenderTest.class);

    public void testMain() throws Exception {
        System.out.println(System.currentTimeMillis());
        int numberOfDevices = 50;

        logger.info("Simulating the load of " + numberOfDevices);
        MockDevice[] threads = new MockDevice[numberOfDevices];

        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("../kafka.properties")) {
            assertNotNull(in);
            props.load(in);
        }

        SchemaRetriever schemaRetriever = new SchemaRetriever(props.getProperty("schema.registry.url"));
        URL kafkaURL = new URL(props.getProperty("rest.proxy.url"));
        AvroEncoder keyEncoder = new StringEncoder();
        AvroEncoder valueEncoder = new SpecificRecordEncoder(false);

        KafkaSender<String, SpecificRecord> directSender = new RestSender<>(kafkaURL, schemaRetriever, keyEncoder, valueEncoder);
        KafkaSender<String, SpecificRecord> kafkaThread = new ThreadedKafkaSender<>(directSender);
        kafkaThread.resetConnection();

        try (KafkaSender<String, SpecificRecord> sender = new BatchedKafkaSender<>(kafkaThread, 1000, 250)) {
            Schema stringSchema = Schema.create(Schema.Type.STRING);
            for (int i = 0; i < numberOfDevices; i++) {
                threads[i] = new MockDevice<>(sender, "device" + i, stringSchema, String.class);
                threads[i].start();
            }
            // stop running after 5 seconds, or after the first thread quits, whichever comes first
            long streamingTimeoutMs = 5_000L;
            if (props.containsKey("streaming.timeout.ms")) {
                try {
                    streamingTimeoutMs = Long.parseLong(props.getProperty("streaming.timeout.ms"));
                } catch (NumberFormatException ex) {
                    // whatever
                }
            }
            threads[0].join(streamingTimeoutMs);
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
