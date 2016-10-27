package org.radarcns.kafka.rest;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.EncoderFactory;

import java.io.IOException;

public class GenericRecordEncoder implements AvroEncoder<GenericRecord> {
    private final EncoderFactory encoderFactory;

    public GenericRecordEncoder() {
        this.encoderFactory = EncoderFactory.get();
    }

    @Override
    public AvroWriter<GenericRecord> writer(Schema schema) throws IOException {
        return new AvroRecordWriter<>(encoderFactory, schema, new GenericDatumWriter<GenericRecord>(schema));
    }
}
