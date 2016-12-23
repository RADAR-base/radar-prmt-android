package org.radarcns.kafka.rest;

import android.support.annotation.NonNull;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import okio.Sink;

public class RestSender<K, V> implements KafkaSender<K, V> {
    private final static Logger logger = LoggerFactory.getLogger(RestSender.class);
    private final AvroEncoder keyEncoder;
    private final AvroEncoder valueEncoder;
    private final JsonFactory jsonFactory;
    public final static String KAFKA_REST_ACCEPT_ENCODING = "application/vnd.kafka.v1+json, application/vnd.kafka+json, application/json";
    public final static MediaType KAFKA_REST_AVRO_ENCODING = MediaType.parse("application/vnd.kafka.avro.v1+json; charset=utf-8");

    private URL kafkaUrl;
    private HttpUrl schemalessKeyUrl;
    private HttpUrl schemalessValueUrl;
    private Request isConnectedRequest;
    private SchemaRetriever schemaRetriever;
    private OkHttpClient httpClient;

    /**
     * Construct a RestSender.
     * @param kafkaUrl url to send data to
     * @param schemaRetriever Retriever of avro schemas
     * @param keyEncoder Avro encoder for keys
     * @param valueEncoder Avro encoder for values
     * @param connectionTimeout socket connection timeout in seconds
     */
    public RestSender(@NonNull URL kafkaUrl, @NonNull SchemaRetriever schemaRetriever, @NonNull AvroEncoder keyEncoder, @NonNull AvroEncoder valueEncoder, long connectionTimeout) {
        setKafkaUrl(kafkaUrl);
        setSchemaRetriever(schemaRetriever);
        this.keyEncoder = keyEncoder;
        this.valueEncoder = valueEncoder;
        jsonFactory = new JsonFactory();
        setConnectionTimeout(connectionTimeout);
    }

    public final synchronized void setConnectionTimeout(long connectionTimeout) {
        if (httpClient != null && httpClient.connectTimeoutMillis() == connectionTimeout) {
            return;
        }
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectionTimeout, TimeUnit.SECONDS)
                .writeTimeout(connectionTimeout, TimeUnit.SECONDS)
                .readTimeout(connectionTimeout, TimeUnit.SECONDS)
                .build();
    }

    private synchronized OkHttpClient getHttpClient() {
        return httpClient;
    }

    public final synchronized void setKafkaUrl(@NonNull URL kafkaUrl) {
        if (kafkaUrl.equals(this.kafkaUrl)) {
            return;
        }
        this.kafkaUrl = kafkaUrl;

        try {
            schemalessKeyUrl = HttpUrl.get(new URL(kafkaUrl, "topics/schemaless-key"));
            schemalessValueUrl = HttpUrl.get(new URL(kafkaUrl, "topics/schemaless-value"));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Schemaless topics do not have a valid URL");
        }

        isConnectedRequest = new Request.Builder().url(kafkaUrl).head().build();
    }

    public final synchronized void setSchemaRetriever(@NonNull SchemaRetriever retriever) {
        this.schemaRetriever = retriever;
    }

    private synchronized SchemaRetriever getSchemaRetriever() {
        return this.schemaRetriever;
    }

    private synchronized HttpUrl getSchemalessValueUrl() {
        return schemalessValueUrl;
    }

    private synchronized HttpUrl getSchemalessKeyUrl() {
        return schemalessKeyUrl;
    }

    private synchronized Request getIsConnectedRequest() {
        return isConnectedRequest;
    }

    class RestTopicSender<L extends K, W extends V> implements KafkaTopicSender<L, W> {
        long lastOffsetSent = -1L;
        final AvroTopic<L, W> topic;
        final HttpUrl url;
        final TopicRequestBody requestBody;

        RestTopicSender(AvroTopic<L, W> topic) throws IOException {
            this.topic = topic;
            URL rawUrl = new URL(kafkaUrl, "topics/" + topic.getName());
            url = HttpUrl.get(rawUrl);
            if (url == null) {
                throw new MalformedURLException("Cannot parse " + rawUrl);
            }
            requestBody = new TopicRequestBody();
        }

        @Override
        public void send(long offset, L key, W value) throws IOException {
            List<Record<L, W>> records = new ArrayList<>(1);
            records.add(new Record<>(offset, key, value));
            send(records);
        }

        /**
         * Actually make a REST request to the Kafka REST server and Schema Registry.
         *
         * @param records values to send
         * @throws IOException if records could not be sent
         */
        @Override
        public void send(@NonNull List<Record<L, W>> records) throws IOException {
            if (records.isEmpty()) {
                return;
            }
            // Get schema IDs
            Schema valueSchema = topic.getValueSchema();
            String sendTopic = topic.getName();

            HttpUrl sendToUrl = url;

            try {
                ParsedSchemaMetadata metadata = getSchemaRetriever().getOrSetSchemaMetadata(sendTopic, false, topic.getKeySchema());
                requestBody.keySchemaId = metadata.getId();
            } catch (IOException ex) {
                sendToUrl = getSchemalessKeyUrl();
                requestBody.keySchemaId = null;
            }
            requestBody.keySchemaString = requestBody.keySchemaId == null ? topic.getKeySchema().toString() : null;

            try {
                ParsedSchemaMetadata metadata = getSchemaRetriever().getOrSetSchemaMetadata(sendTopic, true, valueSchema);
                requestBody.valueSchemaId = metadata.getId();
            } catch (IOException ex) {
                sendToUrl = getSchemalessValueUrl();
                requestBody.valueSchemaId = null;
            }
            requestBody.valueSchemaString = requestBody.valueSchemaId == null ? topic.getValueSchema().toString() : null;
            requestBody.records = records;

            Request request = new Request.Builder()
                    .url(sendToUrl)
                    .addHeader("Accept", KAFKA_REST_ACCEPT_ENCODING)
                    .post(requestBody)
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                // Evaluate the result
                if (response.isSuccessful()) {
                    if (logger.isDebugEnabled()) {
                        logger.info("Added message to topic {} -> {}", sendTopic, response.body().string());
                    }
                    lastOffsetSent = records.get(records.size() - 1).offset;
                } else {
                    String content = response.body().string();
                    logger.error("FAILED to transmit message: {} -> {}...", content, requestBody.content().substring(0, 255));
                    throw new IOException("Failed to submit (HTTP status code " + response.code() + "): " + content);
                }
            } catch (IOException ex) {
                logger.error("FAILED to transmit message:\n{}...", requestBody.content().substring(0, 255) );
                throw ex;
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
            final AvroEncoder.AvroWriter<L> keyWriter;
            final AvroEncoder.AvroWriter<W> valueWriter;

            List<Record<L, W>> records;

            TopicRequestBody() throws IOException {
                keyWriter = keyEncoder.writer(topic.getKeySchema(), topic.getKeyClass());
                valueWriter = valueEncoder.writer(topic.getValueSchema(), topic.getValueClass());
            }

            @Override
            public MediaType contentType() {
                return KAFKA_REST_AVRO_ENCODING;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                try (OutputStream out = sink.outputStream(); JsonGenerator writer = jsonFactory.createGenerator(out, JsonEncoding.UTF8)) {
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

                    for (Record<L, W> record : records) {
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

            String content() throws IOException {
                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    try (Sink sink = Okio.sink(out); BufferedSink bufferedSink = Okio.buffer(sink)) {
                        writeTo(bufferedSink);
                    }
                    return out.toString();
                }
            }
        }
    }

    @Override
    public <L extends K, W extends V> KafkaTopicSender<L, W> sender(AvroTopic<L, W> topic) throws IOException {
        return new RestTopicSender<>(topic);
    }

    @Override
    public boolean resetConnection() {
        return isConnected();
    }

    public boolean isConnected() {
        try (Response response = getHttpClient().newCall(getIsConnectedRequest()).execute()) {
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
