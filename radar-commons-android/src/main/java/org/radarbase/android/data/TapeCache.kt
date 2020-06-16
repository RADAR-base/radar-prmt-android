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

//import com.crashlytics.android.Crashlytics
import org.apache.avro.generic.GenericData
import org.radarbase.android.kafka.KafkaDataSubmitter.Companion.SIZE_LIMIT_DEFAULT
import org.radarbase.android.util.ChangeRunner
import org.radarbase.android.util.SafeHandler
import org.radarbase.data.AvroRecordData
import org.radarbase.data.Record
import org.radarbase.data.RecordData
import org.radarbase.topic.AvroTopic
import org.radarbase.util.BackedObjectQueue
import org.radarbase.util.QueueFile
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * Caches measurement on a BackedObjectQueue. Internally, all data is first cached on a local queue,
 * before being written in batches to the BackedObjectQueue, using a single-threaded
 * ExecutorService. Data is retrieved and removed from the queue in a blocking way using that same
 * ExecutorService. Sent messages are not kept, they are immediately removed.
 *
 * @param <K> measurement key type
 * @param <V> measurement value type
</V></K> */
class TapeCache<K: Any, V: Any>
/**
 * TapeCache to cache measurements with
 * @param topic Kafka Avro topic to write data for.
 * @throws IOException if a BackedObjectQueue cannot be created.
 */
@Throws(IOException::class)
constructor(override val file: File,
            override val topic: AvroTopic<K, V>,
            override val readTopic: AvroTopic<Any, Any>,
            private val executor: SafeHandler,
            private val inputFormat: GenericData,
            outputFormat: GenericData,
            config: DataCache.CacheConfiguration) : DataCache<K, V> {

    private val measurementsToAdd = mutableListOf<Record<K, V>>()
    private val serializer = TapeAvroSerializer(topic, inputFormat)
    private val deserializer = TapeAvroDeserializer<Any, Any>(readTopic, outputFormat)

    private var queueFile: QueueFile
    private var queue: BackedObjectQueue<Record<K, V>, Record<Any, Any>>

    private var addMeasurementFuture: SafeHandler.HandlerFuture? = null

    private val configCache = ChangeRunner(config)

    override var config
        get() = executor.compute { configCache.value }
        set(value) = executor.execute {
            configCache.applyIfChanged(value.copy()) {
                queueFile.maximumFileSize = it.maximumSize
            }
        }

    private val maximumSize: Long
        get() = config.maximumSize.takeIf { it <= Int.MAX_VALUE } ?: Int.MAX_VALUE.toLong()

    init {
        queueFile = try {
            QueueFile.newMapped(Objects.requireNonNull(file), maximumSize)
        } catch (ex: IOException) {
            logger.error("TapeCache {} was corrupted. Removing old cache.", file)
            //Crashlytics.logException(ex)
            if (file.delete()) {
                QueueFile.newMapped(file, maximumSize)
            } else {
                throw ex
            }
        }
        this.queue = BackedObjectQueue(queueFile, serializer, deserializer)
    }

    @Throws(IOException::class)
    override fun getUnsentRecords(limit: Int, sizeLimit: Long): RecordData<Any, Any?>? {
        logger.debug("Trying to retrieve records from topic {}", topic)
        try {
            return executor.compute {
                try {
                    val (currentKey, values) = getValidUnsentRecords(limit, sizeLimit)
                            ?: return@compute null

                    return@compute AvroRecordData<Any, Any>(readTopic, currentKey, values)
                } catch (ex: IOException) {
                    fixCorruptQueue(ex)
                    return@compute null
                } catch (ex: IllegalStateException) {
                    fixCorruptQueue(ex)
                    return@compute null
                }
            }
        } catch (ex: InterruptedException) {
            logger.warn("getUnsentRecords was interrupted, returning an empty list", ex)
            Thread.currentThread().interrupt()
            return null
        } catch (ex: ExecutionException) {
            logger.warn("Failed to retrieve records for topic {}", topic, ex)
            val cause = ex.cause
            if (cause is RuntimeException) {
                throw cause
            } else {
                throw IOException("Unknown error occurred", ex)
            }
        }
    }

    private fun getValidUnsentRecords(limit: Int, sizeLimit: Long): Pair<Any, List<Any>>? {
        var currentKey: Any? = null
        lateinit var records: List<Record<Any, Any>?>

        while (currentKey == null) {
            records = queue.peek(limit, sizeLimit).takeUnless { it.isEmpty() }
                    ?: return null

            val nullSize = records.indexOfFirst { it != null }
                    .takeIf { it != -1 }
                    ?: records.size

            if (nullSize > 0) {
                queue.remove(nullSize)
                records = records.subList(nullSize, records.size)
            }
            currentKey = records.firstOrNull()?.key
        }

        return Pair(currentKey, records.asSequence()
                .takeWhile { it?.key == currentKey }
                .filterNotNull()
                .map(Record<Any, Any>::value)
                .toList())
    }

    @Throws(IOException::class)
    override fun getRecords(limit: Int): RecordData<Any, Any>? {
        return getUnsentRecords(limit, SIZE_LIMIT_DEFAULT)?.let { records ->
            AvroRecordData<Any, Any>(records.topic, records.key, records.filterNotNull())
        }
    }

    override val numberOfRecords: Long
        get() = executor.compute { queue.size.toLong() }

    @Throws(IOException::class)
    override fun remove(number: Int) {
        return executor.execute {
            val actualNumber = Math.min(number, queue.size)
            if (actualNumber > 0) {
                logger.debug("Removing {} records from topic {}", actualNumber, topic.name)
                queue.remove(actualNumber)
            }
        }
    }

    override fun addMeasurement(key: K?, value: V?) {
        require(inputFormat.validate(topic.keySchema, key)
                && inputFormat.validate(topic.valueSchema, value)) {
            "Cannot send invalid record to topic $topic with {key: $key, value: $value}"
        }

        executor.execute {
            measurementsToAdd.add(Record<K, V>(key, value))

            if (addMeasurementFuture == null) {
                addMeasurementFuture = executor.delay(config.commitRate, ::doFlush)
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        flush()
        queue.close()
    }

    override fun flush() {
        try {
            executor.await {
                addMeasurementFuture?.runNow()
            }
        } catch (e: InterruptedException) {
            logger.warn("Did not wait for adding measurements to complete.")
        } catch (ex: ExecutionException) {
            logger.warn("Failed to execute flush task", ex)
        }

    }

    private fun doFlush() {
        addMeasurementFuture = null

        if (measurementsToAdd.isEmpty()) {
            return
        }
        try {
            logger.info("Writing {} records to file in topic {}", measurementsToAdd.size, topic.name)
            queue.addAll(measurementsToAdd)
        } catch (ex: IOException) {
            logger.error("Failed to add records", ex)
            throw RuntimeException(ex)
        } catch (ex: IllegalStateException) {
            logger.error("Queue {} is full, not adding records", topic.name)
        } catch (ex: IllegalArgumentException) {
            logger.error("Failed to validate all records; adding individual records instead: {}", ex.message)
            try {
                logger.info("Writing {} records to file in topic {}", measurementsToAdd.size, topic.name)
                for (record in measurementsToAdd) {
                    try {
                        queue.add(record)
                    } catch (ex2: IllegalArgumentException) {
                        //Crashlytics.logException(ex2)
                    }

                }
            } catch (illEx: IllegalStateException) {
                logger.error("Queue {} is full, not adding records", topic.name)
            } catch (ex2: IOException) {
                logger.error("Failed to add record", ex)
                throw RuntimeException(ex)
            }
        } finally {
            measurementsToAdd.clear()
        }
    }

    @Throws(IOException::class)
    private fun fixCorruptQueue(ex: Exception) {
        logger.error("Queue {} was corrupted. Removing cache.", topic.name, ex)
       // Crashlytics.logException(ex)
        try {
            queue.close()
        } catch (ioex: IOException) {
            logger.warn("Failed to close corrupt queue", ioex)
        }

        if (file.delete()) {
            queueFile = QueueFile.newMapped(file, maximumSize)
            queue = BackedObjectQueue(queueFile, serializer, deserializer)
        } else {
            throw IOException("Cannot create new cache.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TapeCache::class.java)
    }
}
