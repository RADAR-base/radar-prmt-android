package org.radarcns.data;

import org.apache.avro.Schema;

import java.io.IOException;

/** Encode Avro values with a given encoder */
public interface AvroEncoder {
    /** Create a new writer. This method is thread-safe, but the class it returns is not. */
    <T> AvroWriter<T> writer(Schema schema, Class<T> clazz) throws IOException;

    interface AvroWriter<T> {
        /** Encode an object. This method is not thread-safe. */
        byte[] encode(T object) throws IOException;
    }
}
