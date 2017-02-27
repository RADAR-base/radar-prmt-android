package org.radarcns.data;

import com.squareup.tape2.ObjectQueue;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.topic.AvroTopic;
import org.radarcns.util.Serialization;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Converts records from an AvroTopic for Tape
 */
public class TapeAvroConverter<K extends SpecificRecord, V extends SpecificRecord>
        implements ObjectQueue.Converter<Record<K, V>> {
    private final byte[] headerBuffer = new byte[12];
    private final AvroEncoder.AvroWriter<K> keyWriter;
    private final AvroEncoder.AvroWriter<V> valueWriter;
    private final AvroDecoder.AvroReader<K> keyReader;
    private final AvroDecoder.AvroReader<V> valueReader;

    public TapeAvroConverter(AvroTopic<K, V> topic) throws IOException {
        SpecificRecordEncoder specificEncoder = new SpecificRecordEncoder(true);
        keyWriter = specificEncoder.writer(topic.getKeySchema(), topic.getKeyClass());
        valueWriter = specificEncoder.writer(topic.getValueSchema(), topic.getValueClass());
        SpecificRecordDecoder specificDecoder = new SpecificRecordDecoder(true);
        keyReader = specificDecoder.reader(topic.getKeySchema(), topic.getKeyClass());
        valueReader = specificDecoder.reader(topic.getValueSchema(), topic.getValueClass());
    }

    @Override
    public Record<K, V> from(byte[] bytes) throws IOException {
        long offset = Serialization.bytesToLong(bytes, 0);
        int keyLength = Serialization.bytesToInt(bytes, 8);
        K key = keyReader.decode(bytes, 12);
        V value = valueReader.decode(bytes, 12 + keyLength);
        return new Record<>(offset, key, value);
    }

    @Override
    public void toStream(Record<K, V> o, OutputStream bytes) throws IOException {
        Serialization.longToBytes(o.offset, headerBuffer, 0);
        byte[] encodedKey = keyWriter.encode(o.key);
        Serialization.intToBytes(encodedKey.length, headerBuffer, 8);
        bytes.write(headerBuffer);
        bytes.write(encodedKey);
        bytes.write(valueWriter.encode(o.value));
    }
}
