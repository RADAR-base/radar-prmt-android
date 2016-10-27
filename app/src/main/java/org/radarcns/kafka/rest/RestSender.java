package org.radarcns.kafka.rest;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import org.apache.avro.Schema;
import org.radarcns.data.AvroEncoder;
import org.radarcns.data.Record;
import org.radarcns.data.RecordList;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.KafkaSender;
import org.radarcns.kafka.ParsedSchemaMetadata;
import org.radarcns.kafka.SchemaRetriever;
import org.radarcns.net.HttpClient;
import org.radarcns.net.HttpOutputStreamHandler;
import org.radarcns.net.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class RestSender<K, V> implements KafkaSender<K, V> {
    private final static Logger logger = LoggerFactory.getLogger(RestSender.class);
    private final SchemaRetriever schemaRetriever;
    private final URL kafkaUrl;
    private final AvroEncoder<? super K> keyEncoder;
    private final AvroEncoder<? super V> valueEncoder;
    private final ConcurrentHashMap<AvroTopic, Long> lastOffsetsSent;
    private final JsonFactory jsonFactory;

    public RestSender(URL kafkaUrl, SchemaRetriever schemaRetriever, AvroEncoder<? super K> keyEncoder, AvroEncoder<? super V> valueEncoder) {
        this.kafkaUrl = kafkaUrl;
        this.schemaRetriever = schemaRetriever;
        this.keyEncoder = keyEncoder;
        this.valueEncoder = valueEncoder;
        this.lastOffsetsSent = new ConcurrentHashMap<>();
        jsonFactory = new JsonFactory();
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
    public void send(final RecordList<K, V> records) throws IOException {
         if (records.isEmpty()) {
                return;
        }
        // Get schema IDs
        AvroTopic topic = records.getTopic();
        Schema valueSchema = topic.getValueSchema();
        String sendTopic = topic.getName();

        ParsedSchemaMetadata metadata = schemaRetriever.getOrSetSchemaMetadata(sendTopic, true, valueSchema);
        final Integer valueSchemaId = metadata.getId();
        final String valueSchemaString = valueSchemaId == null ? metadata.getSchema().toString() : null;

        metadata = schemaRetriever.getOrSetSchemaMetadata(sendTopic, false, topic.getKeySchema());
        final Integer keySchemaId = metadata.getId();
        final String keySchemaString = keySchemaId == null ? metadata.getSchema().toString() : null;

        // Post to Kafka REST server
        final HttpResponse response = HttpClient.request(new URL(kafkaUrl, "topics/" + sendTopic), "POST", new HttpOutputStreamHandler() {
            @Override
            public void handleOutput(OutputStream out) throws IOException {
                try (BufferedOutputStream bufferedOut = new BufferedOutputStream(out); JsonGenerator writer = jsonFactory.createGenerator(bufferedOut, JsonEncoding.UTF8)) {
                    writer.writeStartObject();
                    if (keySchemaId != null) {
                        writer.writeNumberField("key_schema_id", keySchemaId);
                    } else {
                        writer.writeStringField("key_schema", keySchemaString);
                    }

                    if (valueSchemaId != null) {
                        writer.writeNumberField("value_schema_id", valueSchemaId);
                    } else {
                        writer.writeStringField("value_schema", valueSchemaString);
                    }

                    // Encode Avro records
                    AvroEncoder.AvroWriter<? super K> keyWriter = keyEncoder.writer(records.getTopic().getKeySchema());
                    AvroEncoder.AvroWriter<? super V> valueWriter = valueEncoder.writer(records.getTopic().getValueSchema());

                    writer.writeArrayFieldStart("records");

                    for (Record<K, V> record : records) {
                        writer.writeStartObject();
                        writer.writeFieldName("key");
                        writer.writeRawValue(new String(keyWriter.encode(record.key)));
                        writer.writeFieldName("value");
                        writer.writeRawValue(new String(valueWriter.encode(record.value)));
                        writer.writeEndObject();
                    }
                    writer.writeEndArray();
                    writer.writeEndObject();
                }
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
                logger.debug("Failed to make heartbeat request to {} (HTTP status code {}): {}", new Object[] {kafkaUrl, response.getStatusCode(), response.getContent()});
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
