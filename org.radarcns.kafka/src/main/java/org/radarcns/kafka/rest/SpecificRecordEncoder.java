package org.radarcns.kafka.rest;

import org.apache.avro.Schema;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

import java.io.IOException;

public class SpecificRecordEncoder implements AvroEncoder<SpecificRecord> {
    private final EncoderFactory encoderFactory;

    public SpecificRecordEncoder() {
        this.encoderFactory = EncoderFactory.get();
    }

    @Override
    public AvroWriter<SpecificRecord> writer(Schema schema) throws IOException {
        return new AvroRecordWriter<>(encoderFactory, schema, new SpecificDatumWriter<SpecificRecord>(schema));
    }
}
