package org.radarcns.kafka;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;

/** AvroTopic with schema */
public class AvroTopic {
    private final String name;
    private final Schema valueSchema;
    private final Schema keySchema;
    private final Schema.Field timeField;
    private final Schema.Field timeReceivedField;

    public AvroTopic(String name, SchemaRetriever retriever) throws IOException {
        this(name, Schema.create(Schema.Type.STRING),
                retriever.getSchemaMetadata(name, true).getSchema());
    }

    public AvroTopic(String name, Schema keySchema, Schema valueSchema) {
        if (name == null) {
            throw new IllegalArgumentException("Name may not be null");
        }
        this.name = name;
        this.keySchema = keySchema;
        this.valueSchema = valueSchema;
        this.timeField = this.valueSchema.getField("time");
        if (timeField == null) {
            throw new IllegalArgumentException("Schema must have time as its first field");
        }
        this.timeReceivedField = this.valueSchema.getField("timeReceived");
        if (timeReceivedField == null) {
            throw new IllegalArgumentException("Schema must have timeReceived as its second field");
        }
    }

    public Schema getKeySchema() {
        return keySchema;
    }

    public Schema getValueSchema() {
        return valueSchema;
    }

    public Schema.Field getValueField(String name) {
        Schema.Field field = valueSchema.getField(name);
        if (field == null) {
            throw new IllegalArgumentException("Field " + name + " not in value valueSchema");
        }
        return field;
    }

    public GenericRecord createValueRecord(double time, Object... values) {
        GenericRecord avroRecord = new GenericData.Record(valueSchema);
        avroRecord.put(timeField.pos(), time);
        avroRecord.put(timeReceivedField.pos(), System.currentTimeMillis() / 1000.0);
        for (int i = 0; i < values.length; i += 2) {
            if (values[i] instanceof Schema.Field) {
                avroRecord.put(((Schema.Field) values[i]).pos(), values[i + 1]);
            } else if (values[i] instanceof String) {
                avroRecord.put((String) values[i], values[i + 1]);
            } else {
                throw new IllegalArgumentException("Record key " + values[i] +
                        " is not a Schema.Field or String");
            }
        }
        return avroRecord;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AvroTopic topic = (AvroTopic) o;

        return name.equals(topic.name) && keySchema.equals(topic.keySchema) &&
                valueSchema.equals(topic.valueSchema);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
