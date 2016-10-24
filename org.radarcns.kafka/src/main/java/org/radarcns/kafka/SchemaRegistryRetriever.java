package org.radarcns.kafka;

import org.apache.avro.Schema;
import org.radarcns.kafka.ParsedSchemaMetadata;
import org.radarcns.kafka.SchemaRetriever;

import java.io.IOException;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;


public class SchemaRegistryRetriever extends SchemaRetriever {
    private final SchemaRegistryClient schemaClient;

    public SchemaRegistryRetriever(String url) {
        this.schemaClient = new CachedSchemaRegistryClient(url, 1024);
    }

    @Override
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

    @Override
    public void addSchemaMetadata(String topic, boolean ofValue, ParsedSchemaMetadata metadata) throws IOException {
        if (metadata.getId() == null) {
            try {
                metadata.setId(schemaClient.register(subject(topic, ofValue), metadata.getSchema()));
            } catch (RestClientException ex) {
                throw new IOException(ex);
            }
        }
        super.addSchemaMetadata(topic, ofValue, metadata);
    }
}
