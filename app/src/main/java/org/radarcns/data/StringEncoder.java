package org.radarcns.data;

import org.apache.avro.Schema;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

import java.io.IOException;

/** Encodes a String as Avro */
public class StringEncoder implements AvroEncoder, AvroEncoder.AvroWriter<String> {
    private final static ObjectWriter jsonEncoder = new ObjectMapper().writer();

    @Override
    public <T> AvroWriter<T> writer(Schema schema, Class<T> clazz) {
        if (schema.getType() != Schema.Type.STRING || !clazz.equals(String.class)) {
            throw new IllegalArgumentException("Cannot encode String with a different type than STRING.");
        }
        // noinspection unchecked
        return (AvroWriter<T>)this;
    }

    @Override
    public byte[] encode(String object) throws IOException {
        return jsonEncoder.writeValueAsBytes(object);
    }
}
