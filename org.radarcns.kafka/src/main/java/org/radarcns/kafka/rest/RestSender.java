package org.radarcns.kafka.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.avro.Schema;
import org.radarcns.kafka.ParsedSchemaMetadata;
import org.radarcns.kafka.SchemaRetriever;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.KafkaSender;
import org.radarcns.kafka.Record;
import org.radarcns.kafka.RecordList;
import org.radarcns.net.HttpClient;
import org.radarcns.net.HttpOutputStreamHandler;
import org.radarcns.net.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class RestSender<K, V> implements KafkaSender<K, V> {
    private final static Logger logger = LoggerFactory.getLogger(RestSender.class);
    private final SchemaRetriever schemaRetriever;
    private final URL kafkaUrl;
    private final AvroEncoder<K> keyEncoder;
    private final AvroEncoder<V> valueEncoder;
    private final ConcurrentHashMap<AvroTopic, Long> lastOffsetsSent;

    public RestSender(URL kafkaUrl, SchemaRetriever schemaRetriever, AvroEncoder<K> keyEncoder, AvroEncoder<V> valueEncoder) {
        this.kafkaUrl = kafkaUrl;
        this.schemaRetriever = schemaRetriever;
        this.keyEncoder = keyEncoder;
        this.valueEncoder = valueEncoder;
        this.lastOffsetsSent = new ConcurrentHashMap<>();
    }

    @Override
    public void configure(Properties properties) {
        // noop
    }

    @Override
    public void send(AvroTopic topic, long offset, K key, V value) throws IOException {
        RecordList<K, V> records = new RecordList<>(topic);
        records.add(offset, key, value);
        send(records);
    }

    /**
     * Actually make a REST request to the Kafka REST server and Schema Registry.
     * @param records values to send
     * @throws IOException if records could not be sent
     */
    @Override
    public void send(RecordList<K, V> records) throws IOException {
         if (records.isEmpty()) {
                return;
        }
        // Initialize empty Kafka REST proxy request
        final KafkaRestRequest request = new KafkaRestRequest();
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // Get schema IDs
        AvroTopic topic = records.getTopic();
        Schema valueSchema = topic.getValueSchema();
        String sendTopic = topic.getName();

        ParsedSchemaMetadata metadata = schemaRetriever.getOrSetSchemaMetadata(sendTopic, true, valueSchema);
        if (metadata.getId() != null) {
            request.value_schema_id = metadata.getId();
        } else {
            logger.warn("Cannot get value schema, submitting data to the schema-less topic.");
            request.value_schema = metadata.getSchema().toString();
            sendTopic = "schemaless-value";
        }
        metadata = schemaRetriever.getOrSetSchemaMetadata(sendTopic, false, topic.getKeySchema());
        if (metadata.getId() != null) {
            request.key_schema_id = metadata.getId();
        } else {
            logger.warn("Cannot get key schema, submitting data to the schema-less topic.");
            request.key_schema = metadata.getSchema().toString();
            sendTopic = "schemaless-key";
        }

        // Encode Avro records
        request.records = new ArrayList<>(records.size());
        AvroEncoder.AvroWriter<K> keyWriter = keyEncoder.writer(topic.getKeySchema());
        AvroEncoder.AvroWriter<V> valueWriter = valueEncoder.writer(topic.getValueSchema());

        for (Record<K, V> record : records) {
            try {
                String rawKey = keyWriter.encode(record.key);
                String rawValue = valueWriter.encode(record.value);
                request.records.add(new RawRecord(rawKey, rawValue));
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot encode record", e);
            }
        }

        // Post to Kafka REST server
        HttpResponse response = HttpClient.request(new URL(kafkaUrl, "topics/" + sendTopic), "POST", new HttpOutputStreamHandler() {
            @Override
            public void handleOutput(OutputStream out) throws IOException {
                mapper.writeValue(out, request);
            }
        }, null);

        // Evaluate the result
        if (response.getStatusCode() < 400) {
            logger.debug("Added message to topic {} -> {}", sendTopic, response.getContent());
            lastOffsetsSent.put(topic, records.getLastOffset());
        } else {
            logger.error("FAILED to transmit message: {}", response.getContent());
            throw new IOException("Failed to submit (HTTP status code " + response.getStatusCode() + "): " + response.getContent());
        }
    }

    @Override
    public long getLastSentOffset(AvroTopic topic) {
        Long offset = lastOffsetsSent.get(topic);
        return offset == null ? -1L : offset;
    }

    @Override
    public boolean resetConnection() {
        return isConnected();
    }

    public boolean isConnected() {
        try {
            HttpResponse response = HttpClient.head(kafkaUrl, null);
            if (response.getStatusCode() < 400) {
                return true;
            } else {
                logger.debug("Failed to make heartbeat request to {} (HTTP status code {}): {}", kafkaUrl, response.getStatusCode(), response.getContent());
                return false;
            }
        } catch (IOException ex) {
            logger.debug("Failed to make heartbeat request to {}", kafkaUrl, ex);
            return false;
        }
    }

    @Override
    public void clear() {
        // noop
    }

    @Override
    public void flush() {
        // noop
    }

    @Override
    public void close() {
        // noop
    }
}
