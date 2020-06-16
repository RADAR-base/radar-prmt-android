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

import android.content.Context
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.specific.SpecificData
import org.apache.avro.specific.SpecificRecord
import org.radarbase.android.util.SafeHandler
import org.radarbase.topic.AvroTopic
import org.radarbase.util.SynchronizedReference
import org.radarcns.kafka.ObservationKey
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.*

object CacheStore {
    private val tables: MutableMap<String, SynchronizedReference<DataCacheGroup<*, *>>> = HashMap()
    private val handler = SafeHandler.getInstance("DataCache", THREAD_PRIORITY_BACKGROUND)

    init {
        handler.start()
    }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    @Throws(IOException::class)
    fun <K: ObservationKey, V: SpecificRecord> getOrCreateCaches(context: Context, topic: AvroTopic<K, V>, config: DataCache.CacheConfiguration): DataCacheGroup<K, V> {
        val ref = tables[topic.name] as SynchronizedReference<DataCacheGroup<K, V>>?
                ?: SynchronizedReference {
                    loadCache(context.cacheDir.absolutePath + "/" + topic.name, topic, config)
                }.also { tables[topic.name] = it as SynchronizedReference<DataCacheGroup<*, *>> }

        return ref.get()
    }

    @Throws(IOException::class)
    private fun <K: Any, V: Any> loadCache(base: String, topic: AvroTopic<K, V>, config: DataCache.CacheConfiguration): DataCacheGroup<K, V> {
        val fileBases = getFileBases(base)
        logger.debug("Files for topic {}: {}", topic.name, fileBases)

        var activeDataCache: DataCache<K, V>? = null
        val deprecatedDataCaches = ArrayList<ReadableDataCache>()

        for (fileBase in fileBases) {
            val parser = Schema.Parser()
            val keySchemaFile = File(fileBase + KEY_SCHEMA_EXTENSION)
            val valueSchemaFile = File(fileBase + VALUE_SCHEMA_EXTENSION)
            var keySchema = loadSchema(parser, keySchemaFile)
            var valueSchema = loadSchema(parser, valueSchemaFile)

            val tapeFile = File(fileBase + TAPE_EXTENSION)
            var matches = false

            if (keySchema == null) {
                if (valueSchema == null || valueSchema == topic.valueSchema) {
                    keySchema = topic.keySchema
                    matches = true
                    storeSchema(keySchema, keySchemaFile)
                } else {
                    logger.error("Cannot load partially specified schema")
                }
            }

            if (valueSchema == null) {
                if (keySchema == topic.keySchema) {
                    valueSchema = topic.valueSchema
                    matches = true
                    storeSchema(valueSchema, valueSchemaFile)
                } else {
                    logger.error("Cannot load partially specified schema")
                }
            }

            val outputTopic = AvroTopic(topic.name,
                    keySchema, valueSchema,
                    Any::class.java, Any::class.java)

            if (matches
                    || (keySchema == topic.keySchema && valueSchema == topic.valueSchema)) {
                if (activeDataCache != null) {
                    logger.error("Cannot have more than one active cache")
                }

                logger.info("Loading matching data store with schemas {}", tapeFile)
                activeDataCache = TapeCache(
                        tapeFile, topic, outputTopic, handler,
                        specificData, genericData, config)
            } else {
                logger.debug("Loading deprecated data store {}", tapeFile)
                deprecatedDataCaches.add(TapeCache(tapeFile, outputTopic as AvroTopic<*, *>,
                        outputTopic, handler, specificData, genericData, config))
            }
        }

        if (activeDataCache == null) {
            val baseDir = File(base)
            if (!baseDir.exists() && !baseDir.mkdirs()) {
                throw IOException("Cannot make data cache directory")
            }
            activeDataCache = IntRange(0, 99)
                    .map { "$base/cache-$it" }
                    .find { it !in fileBases }
                    ?.let { fileBase ->
                        val tapeFile = File(fileBase + TAPE_EXTENSION)
                        val keySchemaFile = File(fileBase + KEY_SCHEMA_EXTENSION)
                        val valueSchemaFile = File(fileBase + VALUE_SCHEMA_EXTENSION)

                        val outputTopic = AvroTopic(topic.name,
                                topic.keySchema, topic.valueSchema,
                                Any::class.java, Any::class.java)

                        storeSchema(topic.keySchema, keySchemaFile)
                        storeSchema(topic.valueSchema, valueSchemaFile)

                        logger.info("Creating new data store {}", tapeFile)
                        TapeCache(tapeFile, topic, outputTopic, handler, specificData, genericData, config)
                    } ?: throw IOException("No empty slot to store active data cache in.")
        }

        return DataCacheGroup(activeDataCache, deprecatedDataCaches)
    }

    private fun getFileBases(base: String): List<String> {
        val files = ArrayList<String>(2)
        if (File(base + TAPE_EXTENSION).isFile) {
            files += base
        }
        val baseDir = File(base)
        if (baseDir.isDirectory) {
            files += baseDir.listFiles { _, name -> name.endsWith(TAPE_EXTENSION, ignoreCase = true) }
                    .map {
                        val name = it.name
                        base + "/" + name.substring(0, name.length - TAPE_EXTENSION.length)
                    }
        }
        return files
    }

    private val logger = LoggerFactory.getLogger(CacheStore::class.java)

    internal const val TAPE_EXTENSION = ".tape"
    internal const val KEY_SCHEMA_EXTENSION = ".key.avsc"
    internal const val VALUE_SCHEMA_EXTENSION = ".value.avsc"

    val genericData: GenericData = object : GenericData(CacheStore::class.java.classLoader) {
        override fun isFloat(`object`: Any?): Boolean {
            return (`object` is Float
                    && !`object`.isNaN()
                    && !`object`.isInfinite())
        }

        override fun isDouble(`object`: Any?): Boolean {
            return (`object` is Double
                    && !`object`.isNaN()
                    && !`object`.isInfinite())
        }
    }

    val specificData: SpecificData = object : SpecificData(CacheStore::class.java.classLoader) {
        override fun isFloat(`object`: Any?): Boolean {
            return (`object` is Float
                    && !`object`.isNaN()
                    && !`object`.isInfinite())
        }

        override fun isDouble(`object`: Any?): Boolean {
            return (`object` is Double
                    && !`object`.isNaN()
                    && !`object`.isInfinite())
        }
    }

    private fun loadSchema(parser: Schema.Parser, file: File): Schema? {
        return try {
            if (file.isFile) parser.parse(file) else null
        } catch (ex: RuntimeException) {
            logger.error("Failed to load schema", ex)
            null
        } catch (ex: IOException) {
            logger.error("Failed to load schema", ex)
            null
        }

    }

    private fun storeSchema(schema: Schema, file: File) {
        try {
            FileOutputStream(file).use { out ->
                OutputStreamWriter(out, StandardCharsets.UTF_8).use {
                    it.write(schema.toString(false))
                }
            }
        } catch (ex: IOException) {
            logger.error("Cannot write schema", ex)
        }

    }
}
