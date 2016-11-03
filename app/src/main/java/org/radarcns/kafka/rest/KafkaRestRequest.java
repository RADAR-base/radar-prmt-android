package org.radarcns.kafka.rest;

import java.util.List;

/**
 * Structure of a Kafka REST request to upload data
 */
public class KafkaRestRequest {
    public String key_schema;
    public String value_schema;
    public Integer key_schema_id;
    public Integer value_schema_id;
    public List<RawRecord> records;
}
