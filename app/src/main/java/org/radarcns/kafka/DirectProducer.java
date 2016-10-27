//package org.radarcns.kafka;
//
//import org.apache.avro.generic.GenericRecord;
//import org.apache.kafka.clients.producer.KafkaProducer;
//import org.apache.kafka.clients.producer.ProducerConfig;
//import org.apache.kafka.clients.producer.ProducerRecord;
//import org.radarcns.empaticaE4.MockDevice;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Properties;
//
///**
// * Directly sends a message to Kafka using a KafkaProducer
// */
//public class DirectProducer<K, V> implements KafkaSender<K, V> {
//    private final static Logger logger = LoggerFactory.getLogger(DirectProducer.class);
//    private KafkaProducer<K, V> producer;
//    private final Map<AvroTopic, Long> offsetsSent;
//
//    public DirectProducer() {
//        producer = null;
//        this.offsetsSent = new HashMap<>();
//    }
//
//    @Override
//    public void configure(Properties properties) {
//        Properties props = new Properties();
//        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
//                io.confluent.kafka.serializers.KafkaAvroSerializer.class);
//        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
//                io.confluent.kafka.serializers.KafkaAvroSerializer.class);
//        props.put("schema.registry.url", "http://ubuntu:8081");
//        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "ubuntu:9092");
//        if (properties != null) {
//            props.putAll(properties);
//        }
//        producer = new KafkaProducer<>(props);
//    }
//
//    @Override
//    public void send(AvroTopic topic, long offset, K key, V value) {
//        if (producer == null) {
//            throw new IllegalStateException("#configure() was not called.");
//        }
//        producer.send(new ProducerRecord<>(topic.getName(), key, value));
//
//        offsetsSent.put(topic, offset);
//    }
//
//    @Override
//    public void send(RecordList<K, V> records) {
//        if (producer == null) {
//            throw new IllegalStateException("#configure() was not called.");
//        }
//        AvroTopic topic = records.getTopic();
//        for (Record<K, V> record : records) {
//            producer.send(new ProducerRecord<>(topic.getName(), record.key, record.value));
//        }
//        offsetsSent.put(topic, records.getLastOffset());
//    }
//
//    @Override
//    public long getLastSentOffset(AvroTopic topic) {
//        Long offset = offsetsSent.get(topic);
//        return offset == null ? -1L : offset;
//    }
//
//    @Override
//    public boolean resetConnection() {
//        return true;
//    }
//
//    @Override
//    public boolean isConnected() {
//        return true;
//    }
//
//    @Override
//    public void clear() {
//        // noop
//    }
//
//    @Override
//    public void flush() {
//        if (producer == null) {
//            throw new IllegalStateException("#configure() was not called.");
//        }
//        producer.flush();
//    }
//
//    @Override
//    public void close() {
//        if (producer == null) {
//            throw new IllegalStateException("#configure() was not called.");
//        }
//        producer.close();
//    }
//
//    public static void main(String[] args) throws InterruptedException {
//        int numberOfDevices = 1;
//        if (args.length > 0) {
//            numberOfDevices = Integer.parseInt(args[0]);
//        }
//
//        logger.info("Simulating the load of " + numberOfDevices);
//        MockDevice[] threads = new MockDevice[numberOfDevices];
//        KafkaSender<String, GenericRecord>[] senders = new KafkaSender[numberOfDevices];
//        SchemaRetriever schemaRetriever = new LocalSchemaRetriever();
//        for (int i = 0; i < numberOfDevices; i++) {
//            senders[i] = new DirectProducer<>();
//            senders[i].configure(null);
//            threads[i] = new MockDevice(senders[i], "device" + i, schemaRetriever);
//            threads[i].start();
//        }
//        for (MockDevice device : threads) {
//            device.waitFor();
//        }
//        for (KafkaSender<String, GenericRecord> sender : senders) {
//            try {
//                sender.close();
//            } catch (IOException e) {
//                logger.warn("Failed to close sender", e);
//            }
//        }
//    }
//}
