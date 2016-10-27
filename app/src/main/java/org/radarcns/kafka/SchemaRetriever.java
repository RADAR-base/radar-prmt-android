package org.radarcns.kafka;

import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;

/** Retriever of an Avro Schema */
public class SchemaRetriever {
    private final static Logger logger = LoggerFactory.getLogger(SchemaRetriever.class);
    private final ConcurrentMap<String, ParsedSchemaMetadata> cache;
    private final CachedSchemaRegistryClient schemaClient;

    public SchemaRetriever(String url) {
        cache = new ConcurrentHashMap<>();
        this.schemaClient = new CachedSchemaRegistryClient(url, 1024);
    }

    /** The subject in the Avro Schema Registry, given a Kafka topic. */
    protected String subject(String topic, boolean ofValue) {
        return topic + (ofValue ? "-value" : "-key");
    }

    /** Retrieve schema metadata */
    protected ParsedSchemaMetadata retrieveSchemaMetadata(String topic, boolean ofValue) throws IOException {
        String subject = subject(topic, ofValue);

        try {
            SchemaMetadata metadata = schemaClient.getLatestSchemaMetadata(subject);
            Schema schema = parseSchema(metadata.getSchema());
            return new ParsedSchemaMetadata(metadata.getId(), metadata.getVersion(), schema);
        } catch (RestClientException ex) {
            throw new IOException(ex);
        }
    }

    public ParsedSchemaMetadata getSchemaMetadata(String topic, boolean ofValue) throws IOException {
        ParsedSchemaMetadata value = cache.get(subject(topic, ofValue));
        if (value == null) {
            value = retrieveSchemaMetadata(topic, ofValue);
            ParsedSchemaMetadata oldValue = cache.putIfAbsent(subject(topic, ofValue), value);
            if (oldValue != null) {
                value = oldValue;
            }
        }
        return value;
    }

    /** Parse a schema from string. */
    protected Schema parseSchema(String schemaString) {
        Schema.Parser parser = new Schema.Parser();
        return parser.parse(schemaString);
    }

    /**
     * Add schema metadata to the retriever.
     *
     * This implementation only adds it to the cache.
     */
    public void addSchemaMetadata(String topic, boolean ofValue, ParsedSchemaMetadata metadata) throws IOException {
        if (metadata.getId() == null) {
            try {
                metadata.setId(schemaClient.register(subject(topic, ofValue), metadata.getSchema()));
            } catch (RestClientException ex) {
                throw new IOException(ex);
            }
        }
        cache.put(subject(topic, ofValue), metadata);
    }

    /**
     * Get schema metadata, and if none is found, add a new schema.
     */
    public ParsedSchemaMetadata getOrSetSchemaMetadata(String topic, boolean ofValue, Schema schema) {
        try {
            return getSchemaMetadata(topic, ofValue);
        } catch (IOException ex) {
            logger.warn("Schema for {} value was not yet added to the schema registry.", topic);
        }

        ParsedSchemaMetadata metadata = new ParsedSchemaMetadata(null, null, schema);
        try {
            addSchemaMetadata(topic, ofValue, metadata);
        } catch (IOException ex) {
            logger.error("Failed to add schema for {} value", topic, ex);
        }
        return metadata;
    }
}
