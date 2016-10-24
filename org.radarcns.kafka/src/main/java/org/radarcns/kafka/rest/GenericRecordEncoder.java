package org.radarcns.kafka.rest;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class GenericRecordEncoder implements AvroEncoder<GenericRecord> {
    private final EncoderFactory encoderFactory;

    public GenericRecordEncoder() {
        this.encoderFactory = EncoderFactory.get();
    }

    @Override
    public AvroWriter<GenericRecord> writer(Schema schema) throws IOException {
        return new GenericRecordWriter(schema);
    }

    class GenericRecordWriter implements AvroWriter<GenericRecord> {
        final Encoder encoder;
        final Schema schema;
        final ByteArrayOutputStream out;
        GenericRecordWriter(Schema schema) throws IOException {
            this.schema = schema;
            out = new ByteArrayOutputStream();
            encoder = encoderFactory.jsonEncoder(schema, out);
        }

        public String encode(GenericRecord record) throws IOException {
            GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(schema);
            try {
                writer.write(record, encoder);
                encoder.flush();
                return new String(out.toByteArray());
            } finally {
                out.reset();
            }
        }
    }
}
