package org.radarcns.android.data;

import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.radarcns.data.Record;
import org.radarcns.topic.AvroTopic;
import org.radarcns.util.BackedObjectQueue;
import org.radarcns.util.QueueFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Converts records from an AvroTopic for Tape
 */
public class TapeAvroConverter<K extends SpecificRecord, V extends SpecificRecord>
        implements BackedObjectQueue.Converter<Record<K, V>> {
    private final EncoderFactory encoderFactory;
    private final DecoderFactory decoderFactory;
    private final SpecificDatumWriter<K> keyWriter;
    private final SpecificDatumWriter<V> valueWriter;
    private final SpecificDatumReader<K> keyReader;
    private final SpecificDatumReader<V> valueReader;
    private final byte[] headerBuffer = new byte[8];
    private BinaryEncoder encoder;
    private BinaryDecoder decoder;

    public TapeAvroConverter(AvroTopic<K, V> topic) throws IOException {
        encoderFactory = EncoderFactory.get();
        decoderFactory = DecoderFactory.get();
        keyWriter = new SpecificDatumWriter<>(topic.getKeySchema());
        valueWriter = new SpecificDatumWriter<>(topic.getValueSchema());
        keyReader = new SpecificDatumReader<>(topic.getKeySchema());
        valueReader = new SpecificDatumReader<>(topic.getValueSchema());
        encoder = null;
        decoder = null;
    }

    public Record<K, V> deserialize(InputStream in) throws IOException {
        int numRead = 0;
        do {
            numRead += in.read(headerBuffer, numRead, 8 - numRead);
        } while (numRead < 8);
        decoder = decoderFactory.binaryDecoder(in, decoder);

        long kafkaOffset = QueueFile.bytesToLong(headerBuffer, 0);
        K key = keyReader.read(null, decoder);
        V value = valueReader.read(null, decoder);
        return new Record<>(kafkaOffset, key, value);
    }

    public void serialize(Record<K, V> o, OutputStream out) throws IOException {
        QueueFile.longToBytes(o.offset, headerBuffer, 0);
        out.write(headerBuffer, 0, 8);
        encoder = encoderFactory.binaryEncoder(out, encoder);
        keyWriter.write(o.key, encoder);
        valueWriter.write(o.value, encoder);
        encoder.flush();
    }
}
