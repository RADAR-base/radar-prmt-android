/*
 * Copyright 2017 The Hyve
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarbase.util;

import org.apache.avro.specific.SpecificData;

/**
 * Created by joris on 27/07/2017.
 */
@SuppressWarnings("all")
/** Audio recording actively activated by a user. */
@org.apache.avro.specific.AvroGenerated
public class ActiveAudioRecording extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
    private static final long serialVersionUID = -200182898891048587L;
    public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"ActiveAudioRecording\",\"namespace\":\"org.radarcns.util\",\"doc\":\"Audio recording actively activated by a user.\",\"fields\":[{\"name\":\"data\",\"type\":\"bytes\",\"doc\":\"raw audio data\"}]}");

    public static org.apache.avro.Schema getClassSchema() {
        return SCHEMA$;
    }

    /**
     * raw audio data
     */
    @Deprecated
    public java.nio.ByteBuffer data;

    /**
     * Default constructor.  Note that this does not initialize fields
     * to their default values from the schema.  If that is desired then
     * one should use <code>newBuilder()</code>.
     */
    public ActiveAudioRecording() {
    }

    /**
     * All-args constructor.
     *
     * @param time         start-time of the recording in UTC (s)
     * @param timeReceived time that the audio was processed in UTC (s)
     * @param format       Audio format used, null if unknown or not a standard audio format.
     * @param encoding     Audio encoding used, null if unknown or not a standard audio encoding.
     * @param data         raw audio data
     */
    public ActiveAudioRecording(java.nio.ByteBuffer data) {
        this.data = data;
    }

    public org.apache.avro.Schema getSchema() {
        return SCHEMA$;
    }

    // Used by DatumWriter.  Applications should not call.
    public Object get(int field$) {
        switch (field$) {
            case 0:
                return data;
            default:
                throw new org.apache.avro.AvroRuntimeException("Bad index");
        }
    }

    // Used by DatumReader.  Applications should not call.
    @SuppressWarnings(value = "unchecked")
    public void put(int field$, Object value$) {
        switch (field$) {
            case 0:
                data = (java.nio.ByteBuffer) value$;
                break;
            default:
                throw new org.apache.avro.AvroRuntimeException("Bad index");
        }
    }

    /**
     * Gets the value of the 'data' field.
     *
     * @return raw audio data
     */
    public java.nio.ByteBuffer getData() {
        return data;
    }

    /**
     * Sets the value of the 'data' field.
     * raw audio data
     *
     * @param value the value to set.
     */
    public void setData(java.nio.ByteBuffer value) {
        this.data = value;
    }

    /**
     * Creates a new ActiveAudioRecording RecordBuilder.
     *
     * @return A new ActiveAudioRecording RecordBuilder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Creates a new ActiveAudioRecording RecordBuilder by copying an existing Builder.
     *
     * @param other The existing builder to copy.
     * @return A new ActiveAudioRecording RecordBuilder
     */
    public static Builder newBuilder(Builder other) {
        return new Builder(other);
    }

    /**
     * Creates a new ActiveAudioRecording RecordBuilder by copying an existing ActiveAudioRecording instance.
     *
     * @param other The existing instance to copy.
     * @return A new ActiveAudioRecording RecordBuilder
     */
    public static Builder newBuilder(ActiveAudioRecording other) {
        return new Builder(other);
    }

    /**
     * RecordBuilder for ActiveAudioRecording instances.
     */
    public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<ActiveAudioRecording>
            implements org.apache.avro.data.RecordBuilder<ActiveAudioRecording> {

        /**
         * raw audio data
         */
        private java.nio.ByteBuffer data;

        /**
         * Creates a new Builder
         */
        private Builder() {
            super(SCHEMA$);
        }

        /**
         * Creates a Builder by copying an existing Builder.
         *
         * @param other The existing Builder to copy.
         */
        private Builder(Builder other) {
            super(other);
            if (isValidValue(fields()[0], other.data)) {
                this.data = data().deepCopy(fields()[0].schema(), other.data);
                fieldSetFlags()[0] = true;
            }
        }

        /**
         * Creates a Builder by copying an existing ActiveAudioRecording instance
         *
         * @param other The existing instance to copy.
         */
        private Builder(ActiveAudioRecording other) {
            super(SCHEMA$);
            if (isValidValue(fields()[0], other.data)) {
                this.data = data().deepCopy(fields()[0].schema(), other.data);
                fieldSetFlags()[0] = true;
            }
        }

        /**
         * Gets the value of the 'data' field.
         * raw audio data
         *
         * @return The value.
         */
        public java.nio.ByteBuffer getData() {
            return data;
        }

        /**
         * Sets the value of the 'data' field.
         * raw audio data
         *
         * @param value The value of 'data'.
         * @return This builder.
         */
        public Builder setData(java.nio.ByteBuffer value) {
            validate(fields()[0], value);
            this.data = value;
            fieldSetFlags()[0] = true;
            return this;
        }

        /**
         * Checks whether the 'data' field has been set.
         * raw audio data
         *
         * @return True if the 'data' field has been set, false otherwise.
         */
        public boolean hasData() {
            return fieldSetFlags()[0];
        }


        /**
         * Clears the value of the 'data' field.
         * raw audio data
         *
         * @return This builder.
         */
        public Builder clearData() {
            data = null;
            fieldSetFlags()[0] = false;
            return this;
        }

        @Override
        public ActiveAudioRecording build() {
            try {
                ActiveAudioRecording record = new ActiveAudioRecording();
                record.data = fieldSetFlags()[0] ? this.data : (java.nio.ByteBuffer) defaultValue(fields()[0]);
                return record;
            } catch (Exception e) {
                throw new org.apache.avro.AvroRuntimeException(e);
            }
        }
    }

    private static final org.apache.avro.io.DatumWriter
            WRITER$ = new org.apache.avro.specific.SpecificDatumWriter(SCHEMA$);

    @SuppressWarnings("unchecked")
    @Override
    public void writeExternal(java.io.ObjectOutput out)
            throws java.io.IOException {
        WRITER$.write(this, SpecificData.getEncoder(out));
    }

    private static final org.apache.avro.io.DatumReader
            READER$ = new org.apache.avro.specific.SpecificDatumReader(SCHEMA$);

    @SuppressWarnings("unchecked")
    @Override
    public void readExternal(java.io.ObjectInput in)
            throws java.io.IOException {
        READER$.read(this, SpecificData.getDecoder(in));
    }

}
