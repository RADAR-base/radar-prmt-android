package org.radarcns.kafka.rest;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * Structure of a single Kafka REST request record
 */
public class RawRecord {
    @JsonRawValue
    public String key;
    // Use a raw value, so we can put JSON in this String.
    @JsonRawValue
    public String value;
    public RawRecord(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
