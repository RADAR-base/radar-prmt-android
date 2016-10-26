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
    private final Schema.Field timeField;
    private final Schema.Field timeReceivedField;
    private final Schema.Type[] valueFieldTypes;
    private Class valueClass;

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

    public Schema.Field getValueField(String name) {
        Schema.Field field = valueSchema.getField(name);
        if (field == null) {
            throw new IllegalArgumentException("Field " + name + " not in value valueSchema");
        }
        return field;
    }

    public Schema.Type[] getValueFieldTypes() {
        return valueFieldTypes;
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

    public static ThreadLocal<AvroTopic> newThreadLocalTopic(String name, SchemaRetriever retriever) throws IOException {
        ThreadLocalTopic localTopic = new ThreadLocalTopic(name, retriever);
        if (localTopic.get() == null) {
            throw new IOException("Cannot find schema of topic " + name);
        }
        return localTopic;
    }
    private static class ThreadLocalTopic extends ThreadLocal<AvroTopic> {
        final String name;
        final SchemaRetriever schemaRetriever;

        ThreadLocalTopic(String name, SchemaRetriever retriever) {
            this.name = name;
            this.schemaRetriever = retriever;
        }
        @Override
        protected AvroTopic initialValue() {
            try {
                return new AvroTopic(name, schemaRetriever);
            } catch (IOException e) {
                logger.error("Topic {} cannot be retrieved", name, e);
                return null;
            }
        }
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
