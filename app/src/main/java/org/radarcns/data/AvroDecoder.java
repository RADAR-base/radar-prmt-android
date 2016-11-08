package org.radarcns.data;

import org.apache.avro.Schema;

import java.io.IOException;

/** Decode Avro values with a given encoder */
public interface AvroDecoder {
    /** Create a new reader. This method is thread-safe, but the class it returns is not. */
    <T> AvroReader<T> reader(Schema schema, Class<T> clazz) throws IOException;

    interface AvroReader<T> {
        /** Encode an object to String. This method is not thread-safe. */
        T decode(byte[] object) throws IOException;
    }
}
