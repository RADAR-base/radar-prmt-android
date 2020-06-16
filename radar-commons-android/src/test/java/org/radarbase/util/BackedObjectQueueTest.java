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

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificData;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.radarbase.android.data.CacheStore;
import org.radarbase.android.data.TapeAvroDeserializer;
import org.radarbase.android.data.TapeAvroSerializer;
import org.radarbase.data.Record;
import org.radarbase.topic.AvroTopic;
import org.radarcns.kafka.ObservationKey;
import org.radarcns.passive.phone.PhoneLight;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by joris on 27/07/2017.
 */
public class BackedObjectQueueTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private static final SpecificData specificData = CacheStore.INSTANCE.getSpecificData();
    private static final GenericData genericData = CacheStore.INSTANCE.getGenericData();

    @Test
    public void testBinaryObject() throws IOException {
        File file = folder.newFile();
        Random random = new Random();
        byte[] data = new byte[176482];
        random.nextBytes(data);

        assertTrue(file.delete());
        AvroTopic<ObservationKey, ActiveAudioRecording> topic = new AvroTopic<>("test",
                ObservationKey.getClassSchema(), ActiveAudioRecording.getClassSchema(),
                ObservationKey.class, ActiveAudioRecording.class);

        AvroTopic<GenericRecord, GenericRecord> outputTopic = new AvroTopic<>("test",
                ObservationKey.getClassSchema(), ActiveAudioRecording.getClassSchema(),
                GenericRecord.class, GenericRecord.class);

        try (BackedObjectQueue<Record<ObservationKey, ActiveAudioRecording>, Record<GenericRecord, GenericRecord>> queue = new BackedObjectQueue<>(
                QueueFile.Companion.newMapped(file, 450000000),
                new TapeAvroSerializer<>(topic, specificData),
                new TapeAvroDeserializer<>(outputTopic, genericData))) {

            ByteBuffer buffer = ByteBuffer.wrap(data);
            Record<ObservationKey, ActiveAudioRecording> record = new Record<>(
                    new ObservationKey("test", "a", "b"), new ActiveAudioRecording(buffer));

            queue.add(record);
            assertEquals(1, queue.getSize());
        }
        try (BackedObjectQueue<Record<ObservationKey, ActiveAudioRecording>, Record<GenericRecord, GenericRecord>> queue = new BackedObjectQueue<>(
                QueueFile.Companion.newMapped(file, 450000000), new TapeAvroSerializer<>(topic, specificData), new TapeAvroDeserializer<>(outputTopic, genericData))) {
            Record<GenericRecord, GenericRecord> result = queue.peek();
            assertNotNull(result);
            assertArrayEquals(data, ((ByteBuffer) result.value.get("data")).array());
        }
    }

    @Test
    public void testRegularObject() throws IOException {
        File file = folder.newFile();
        assertTrue(file.delete());
        AvroTopic<ObservationKey, ObservationKey> topic = new AvroTopic<>("test",
                ObservationKey.getClassSchema(), ObservationKey.getClassSchema(),
                ObservationKey.class, ObservationKey.class);
        AvroTopic<GenericRecord, GenericRecord> outputTopic = new AvroTopic<>("test",
                ObservationKey.getClassSchema(), ObservationKey.getClassSchema(),
                GenericRecord.class, GenericRecord.class);

        BackedObjectQueue<Record<ObservationKey, ObservationKey>, Record<GenericRecord, GenericRecord>> queue;
        queue = new BackedObjectQueue<>(
                QueueFile.Companion.newMapped(file, 10000),
                new TapeAvroSerializer<>(topic, specificData),
                new TapeAvroDeserializer<>(outputTopic, genericData));

        Record<ObservationKey, ObservationKey> record = new Record<>(
                new ObservationKey("test", "a", "b"),
                new ObservationKey("test", "c", "d"));

        queue.add(record);
        queue.peek();
        assertEquals(1, queue.getSize());
    }

    @Test
    public void testFloatObject() throws IOException {
        File file = folder.newFile();
        assertTrue(file.delete());
        AvroTopic<ObservationKey, PhoneLight> topic = new AvroTopic<>("test",
                ObservationKey.getClassSchema(), PhoneLight.getClassSchema(),
                ObservationKey.class, PhoneLight.class);

        AvroTopic<GenericRecord, GenericRecord> outputTopic = new AvroTopic<>("test",
                ObservationKey.getClassSchema(), PhoneLight.getClassSchema(),
                GenericRecord.class, GenericRecord.class);


        BackedObjectQueue<Record<ObservationKey, PhoneLight>, Record<GenericRecord, GenericRecord>> queue;
        queue = new BackedObjectQueue<>(
                QueueFile.Companion.newMapped(file, 10000), new TapeAvroSerializer<>(topic, specificData), new TapeAvroDeserializer<>(outputTopic, genericData));

        double now = System.currentTimeMillis() / 1000d;
        Record<ObservationKey, PhoneLight> record = new Record<>(
                new ObservationKey("test", "a", "b"),
                new PhoneLight(now, now, Float.NaN));

        queue.add(record);
        assertEquals(1, queue.getSize());
        exception.expect(IllegalArgumentException.class);
        queue.peek();
    }
}
