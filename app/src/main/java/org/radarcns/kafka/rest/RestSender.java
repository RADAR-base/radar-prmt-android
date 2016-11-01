package org.radarcns.kafka.rest;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import org.apache.avro.Schema;
import org.radarcns.data.AvroEncoder;
import org.radarcns.data.Record;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.KafkaSender;
import org.radarcns.kafka.KafkaTopicSender;
import org.radarcns.kafka.ParsedSchemaMetadata;
import org.radarcns.kafka.SchemaRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public class RestSender<K, V> implements KafkaSender<K, V> {
    private final static Logger logger = LoggerFactory.getLogger(RestSender.class);
    private final SchemaRetriever schemaRetriever;
    private final URL kafkaUrl;
    private final AvroEncoder<? super K> keyEncoder;
    private final AvroEncoder<? super V> valueEncoder;
    private final JsonFactory jsonFactory;
    private final OkHttpClient httpClient;
    public final static String KAFKA_REST_ACCEPT_ENCODING = "application/vnd.kafka.v1+json, application/vnd.kafka+json, application/json";
    public final static MediaType KAFKA_REST_AVRO_ENCODING = MediaType.parse("application/vnd.kafka.avro.v1+json; charset=utf-8");
    private final Request isConnectedRequest;

    public RestSender(URL kafkaUrl, SchemaRetriever schemaRetriever, AvroEncoder<? super K> keyEncoder, AvroEncoder<? super V> valueEncoder) {
        this.kafkaUrl = kafkaUrl;
        this.schemaRetriever = schemaRetriever;
        this.keyEncoder = keyEncoder;
        this.valueEncoder = valueEncoder;
        jsonFactory = new JsonFactory();
        httpClient = new OkHttpClient();
        isConnectedRequest = new Request.Builder().url(kafkaUrl).head().build();
    }

    class RestTopicSender implements KafkaTopicSender<K, V> {
        long lastOffsetSent = -1L;
        final AvroTopic topic;
        final Request request;
        final TopicRequestBody requestBody;

        RestTopicSender(AvroTopic topic) throws IOException {
            this.topic = topic;
            URL rawUrl = new URL(kafkaUrl, "topics/" + topic.getName());
            HttpUrl url = HttpUrl.get(rawUrl);
            if (url == null) {
                throw new MalformedURLException("Cannot parse " + rawUrl);
            }

            requestBody = new TopicRequestBody();
            request = new Request.Builder()
                    .url(url)
                    .addHeader("Accept", KAFKA_REST_ACCEPT_ENCODING)
                    .post(requestBody)
                    .build();
        }

        @Override
        public void send(long offset, K key, V value) throws IOException {
            List<Record<K, V>> records = new ArrayList<>(1);
            records.add(new Record<>(offset, key, value));
            send(records);
        }

        /**
         * Actually make a REST request to the Kafka REST server and Schema Registry.
         * @param records values to send
         * @throws IOException if records could not be sent
         */
        @Override
        public void send(List<Record<K, V>> records) throws IOException {
            if (records.isEmpty()) {
                return;
            }
            // Get schema IDs
            Schema valueSchema = topic.getValueSchema();
            String sendTopic = topic.getName();

            ParsedSchemaMetadata metadata = schemaRetriever.getOrSetSchemaMetadata(sendTopic, true, valueSchema);
            requestBody.valueSchemaId = metadata.getId();
            requestBody.valueSchemaString = requestBody.valueSchemaId == null ? metadata.getSchema().toString() : null;

            metadata = schemaRetriever.getOrSetSchemaMetadata(sendTopic, false, topic.getKeySchema());
            requestBody.keySchemaId = metadata.getId();
            requestBody.keySchemaString = requestBody.keySchemaId == null ? metadata.getSchema().toString() : null;

            try (Response response = httpClient.newCall(request).execute()) {
                // Evaluate the result
                if (response.isSuccessful()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Added message to topic {} -> {}", sendTopic, response.body().string());
                    }
                    lastOffsetSent = records.get(records.size() - 1).offset;
                } else {
                    String content = response.body().string();
                    logger.error("FAILED to transmit message: {}", content);
                    throw new IOException("Failed to submit (HTTP status code " + response.code() + "): " + content);
                }
            }
        }

        @Override
        public long getLastSentOffset() {
            return lastOffsetSent;
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

        class TopicRequestBody extends RequestBody {
            Integer keySchemaId, valueSchemaId;
            String keySchemaString, valueSchemaString;
            final AvroEncoder.AvroWriter<? super K> keyWriter;
            final AvroEncoder.AvroWriter<? super V> valueWriter;

            List<Record<K, V>> records;

            TopicRequestBody() throws IOException {
                keyWriter = keyEncoder.writer(topic.getKeySchema());
                valueWriter = valueEncoder.writer(topic.getValueSchema());
            }

            @Override
            public MediaType contentType() {
                return KAFKA_REST_AVRO_ENCODING;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                try (JsonGenerator writer = jsonFactory.createGenerator(sink.outputStream(), JsonEncoding.UTF8)) {
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
        }

    }

    @Override
    public KafkaTopicSender<K, V> sender(AvroTopic topic) throws IOException {
        return new RestTopicSender(topic);
    }

    @Override
    public boolean resetConnection() {
        return isConnected();
    }

    public boolean isConnected() {
        try (Response response = httpClient.newCall(isConnectedRequest).execute()) {
            if (response.isSuccessful()) {
                return true;
            } else {
                logger.warn("Failed to make heartbeat request to {} (HTTP status code {}): {}", kafkaUrl, response.code(), response.body().string());
                return false;
            }
        } catch (IOException ex) {
            logger.debug("Failed to make heartbeat request to {}", kafkaUrl, ex);
            return false;
        }
    }

    @Override
    public void close() {
        // noop
    }
}
