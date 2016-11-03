package org.radarcns.kafka;

import junit.framework.TestCase;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DirectProducerTest extends TestCase {

    private final static Logger logger = LoggerFactory.getLogger(DirectProducerTest.class);

    public void testDirect() throws InterruptedException {
        int numberOfDevices = 1;

        logger.info("Simulating the load of " + numberOfDevices);
        MockDevice[] threads = new MockDevice[numberOfDevices];
        KafkaSender[] senders = new KafkaSender[numberOfDevices];
        for (int i = 0; i < numberOfDevices; i++) {
            senders[i] = new DirectProducer<>(null);
            //noinspection unchecked
            threads[i] = new MockDevice<>((KafkaSender<String, SpecificRecord>)senders[i], "device" + i, Schema.create(Schema.Type.STRING), String.class);
            threads[i].start();
        }
        threads[0].join(5_000L);
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
