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

package org.radarbase.android.data

import org.apache.avro.generic.GenericData
import org.apache.avro.io.BinaryEncoder
import org.apache.avro.io.DatumWriter
import org.apache.avro.io.EncoderFactory
import org.radarbase.android.util.ChangeApplier
import org.radarbase.data.Record
import org.radarbase.topic.AvroTopic
import org.radarbase.util.BackedObjectQueue
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Converts records from an AvroTopic for Tape
 */
class TapeAvroSerializer<K: Any, V: Any>(topic: AvroTopic<K, V>, specificData: GenericData) : BackedObjectQueue.Serializer<Record<K, V>> {
    private val encoderFactory: EncoderFactory = EncoderFactory.get()
    @Suppress("UNCHECKED_CAST")
    private val keyWriter: DatumWriter<K> = specificData.createDatumWriter(topic.keySchema) as DatumWriter<K>
    @Suppress("UNCHECKED_CAST")
    private val valueWriter: DatumWriter<V> = specificData.createDatumWriter(topic.valueSchema) as DatumWriter<V>
    private var encoder: BinaryEncoder? = null
    private val cachedKey = ChangeApplier(::serializeKey)

    @Throws(IOException::class)
    override fun serialize(value: Record<K, V>, output: OutputStream) {
        // for backwards compatibility
        output.write(EMPTY_HEADER, 0, 8)
        output.write(cachedKey.applyIfChanged(value.key))
        encoder = encoderFactory.binaryEncoder(output, encoder).also {
            valueWriter.write(value.value, it)
            it.flush()
        }
    }

    private fun serializeKey(key: K): ByteArray {
        val keyOut = ByteArrayOutputStream()
        encoder = encoderFactory.binaryEncoder(keyOut, encoder).also {
            keyWriter.write(key, it)
            it.flush()
        }
        return keyOut.toByteArray()
    }

    companion object {
        private val EMPTY_HEADER = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
    }
}
