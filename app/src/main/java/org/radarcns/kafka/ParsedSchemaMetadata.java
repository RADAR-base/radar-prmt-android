package org.radarcns.kafka;

import org.apache.avro.Schema;

/**
 * Parsed schema metadata from a Schema Registry.
 */
public class ParsedSchemaMetadata {
    private final Integer version;
    private Integer id;
    private final Schema schema;

    public ParsedSchemaMetadata(Integer id, Integer version, Schema schema) {
        this.id = id;
        this.version = version;
        this.schema = schema;
    }

    public Integer getId() {
        return id;
    }

    public Schema getSchema() {
        return schema;
    }

    public Integer getVersion() {
        return version;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
