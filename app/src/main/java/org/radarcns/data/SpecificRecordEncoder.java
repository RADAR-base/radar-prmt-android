package org.radarcns.data;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SpecificRecordEncoder<T extends SpecificRecord> implements AvroEncoder<T> {
    private final EncoderFactory encoderFactory;
    private final boolean binary;

    public SpecificRecordEncoder(boolean binary) {
        this.encoderFactory = EncoderFactory.get();
        this.binary = binary;
    }

    @Override
    public AvroWriter<T> writer(Schema schema) throws IOException {
        return new AvroRecordWriter(encoderFactory, schema, new SpecificDatumWriter<T>(schema));
    }

    class AvroRecordWriter implements AvroEncoder.AvroWriter<T> {
        final Encoder encoder;
        final ByteArrayOutputStream out;
        private final DatumWriter<T> writer;

        AvroRecordWriter(EncoderFactory encoderFactory, Schema schema, DatumWriter<T> writer) throws IOException {
            this.writer = writer;
            out = new ByteArrayOutputStream();
            if (binary) {
                encoder = encoderFactory.binaryEncoder(out, null);
            } else {
                encoder = encoderFactory.jsonEncoder(schema, out);
            }
        }

        public byte[] encode(T record) throws IOException {
            try {
                writer.write(record, encoder);
                encoder.flush();
                return out.toByteArray();
            } finally {
                out.reset();
            }
        }
    }
}
