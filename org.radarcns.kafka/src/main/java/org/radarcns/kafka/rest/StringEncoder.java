package org.radarcns.kafka.rest;

import org.apache.avro.Schema;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

import java.io.IOException;

public class StringEncoder implements AvroEncoder<String>, AvroEncoder.AvroWriter<String> {
    private final static ObjectWriter jsonEncoder = new ObjectMapper().writer();

    @Override
    public AvroWriter<String> writer(Schema schema) {
        if (schema.getType() != Schema.Type.STRING) {
            throw new IllegalArgumentException("Cannot encode String with a different type than STRING.");
        }
        return this;
    }

    @Override
    public String encode(String object) throws IOException {
        return jsonEncoder.writeValueAsString(object);
    }
}
