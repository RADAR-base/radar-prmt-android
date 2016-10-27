package org.radarcns.kafka.rest;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

class AvroRecordWriter<T> implements AvroEncoder.AvroWriter<T> {
    final Encoder encoder;
    final ByteArrayOutputStream out;
    private final DatumWriter<T> writer;

    AvroRecordWriter(EncoderFactory encoderFactory, Schema schema, DatumWriter<T> writer) throws IOException {
        this.writer = writer;
        out = new ByteArrayOutputStream();
        encoder = encoderFactory.jsonEncoder(schema, out);
    }

    public String encode(T record) throws IOException {
        try {
            writer.write(record, encoder);
            encoder.flush();
            return new String(out.toByteArray());
        } finally {
            out.reset();
        }
    }
}
