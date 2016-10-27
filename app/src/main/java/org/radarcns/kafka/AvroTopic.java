package org.radarcns.kafka;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecord;
import org.jboss.netty.logging.InternalLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/** AvroTopic with schema */
public class AvroTopic {
    private final static Logger logger = LoggerFactory.getLogger(AvroTopic.class);

    private final String name;
    private final Schema valueSchema;
    private final Schema keySchema;
    private final Schema.Type[] valueFieldTypes;
    private Class valueClass;

    public AvroTopic(String name, Schema keySchema, Schema valueSchema) {
        if (name == null) {
            throw new IllegalArgumentException("Name may not be null");
        }
        this.name = name;
        this.keySchema = keySchema;
        this.valueSchema = valueSchema;
        if (this.valueSchema.getField("time") == null) {
            throw new IllegalArgumentException("Schema must have time as its first field");
        }
        if (this.valueSchema.getField("timeReceived") == null) {
            throw new IllegalArgumentException("Schema must have timeReceived as a field");
        }
        this.valueClass = null;
        List<Schema.Field> fields = valueSchema.getFields();
        this.valueFieldTypes = new Schema.Type[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            valueFieldTypes[i] = fields.get(i).schema().getType();
        }
    }

    public Schema getKeySchema() {
        return keySchema;
    }

    public Schema getValueSchema() {
        return valueSchema;
    }

    public SpecificRecord newValueInstance() {
        if (valueClass == null) {
            valueClass = SpecificData.get().getClass(valueSchema);
        }
        return (SpecificRecord)SpecificData.newInstance(valueClass, valueSchema);
    }

    public Schema.Type[] getValueFieldTypes() {
        return valueFieldTypes;
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
