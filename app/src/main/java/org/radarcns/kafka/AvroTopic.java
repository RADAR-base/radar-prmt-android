package org.radarcns.kafka;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;

import java.util.List;

/** AvroTopic with schema */
public class AvroTopic<K, V> {
    private final String name;
    private final Schema valueSchema;
    private final Schema keySchema;
    private final Schema.Type[] valueFieldTypes;
    private final Class<V> valueClass;
    private final Class<K> keyClass;

    public AvroTopic(String name, Schema keySchema, Schema valueSchema, Class<K> keyClass, Class<V> valueClass) {
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
        this.valueClass = valueClass;
        this.keyClass = keyClass;
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

    public Class<V> getValueClass() {
        return valueClass;
    }

    public Class<K> getKeyClass() {
        return keyClass;
    }

    /**
     * Tries to construct a new SpecificData instance of the value.
     * @return new empty SpecificData class
     * @throws ClassCastException Value class is not a SpecificData class
     */
    @SuppressWarnings("unchecked")
    public V newValueInstance() throws ClassCastException {
        return (V)SpecificData.newInstance(valueClass, valueSchema);
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

        return name.equals(topic.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
