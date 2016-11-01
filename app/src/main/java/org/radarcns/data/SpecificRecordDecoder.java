package org.radarcns.data;

import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecord;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class SpecificRecordDecoder implements AvroDecoder {
    private final DecoderFactory decoderFactory;
    private final boolean binary;

    public SpecificRecordDecoder(boolean binary) {
        this.decoderFactory = DecoderFactory.get();
        this.binary = binary;
    }

    @Override
    public <T> AvroReader<T> reader(Schema schema, Class<T> clazz) throws IOException {
        if (!SpecificRecord.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Can only create readers for SpecificRecords.");
        }
        return new AvroRecordReader<>(schema, new SpecificDatumReader<T>(schema));
    }

    class AvroRecordReader<T> implements AvroReader<T> {
        private final DatumReader<T> reader;
        private final Schema schema;
        private Decoder decoder;

        AvroRecordReader(Schema schema, DatumReader<T> reader) throws IOException {
            this.reader = reader;
            this.schema = schema;
            this.decoder = null;
        }

        public T decode(byte[] record) throws IOException {
            if (binary) {
                decoder = decoderFactory.binaryDecoder(record, (BinaryDecoder) decoder);
            } else {
                decoder = decoderFactory.jsonDecoder(schema, new ByteArrayInputStream(record));
            }
            return reader.read(null, decoder);
        }
    }
}
