package org.radarcns.kafka;

import junit.framework.TestCase;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.Properties;

public class DirectProducerTest extends TestCase {

    private final static Logger logger = LoggerFactory.getLogger(DirectProducerTest.class);

    public void testDirect() throws InterruptedException, IOException {
        int numberOfDevices = 1;

        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("kafka.properties")) {
            assertNotNull(in);
            props.load(in);
        }

        logger.info("Simulating the load of " + numberOfDevices);
        MockDevice[] threads = new MockDevice[numberOfDevices];
        KafkaSender[] senders = new KafkaSender[numberOfDevices];
        for (int i = 0; i < numberOfDevices; i++) {
            senders[i] = new DirectProducer<>(props);
            //noinspection unchecked
            threads[i] = new MockDevice<>((KafkaSender<String, SpecificRecord>)senders[i], "device" + i, Schema.create(Schema.Type.STRING), String.class);
            threads[i].start();
        }
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
        for (KafkaSender sender : senders) {
            try {
                sender.close();
            } catch (IOException e) {
                logger.warn("Failed to close sender", e);
            }
        }
    }
}
