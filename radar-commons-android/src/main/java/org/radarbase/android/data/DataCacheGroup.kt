package org.radarbase.android.data

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.io.IOException

class DataCacheGroup<K, V>(
        val activeDataCache: DataCache<K, V>,
        val deprecatedCaches: MutableList<ReadableDataCache>) : Closeable {

    val topicName: String = activeDataCache.topic.name

    @Throws(IOException::class)
    fun deleteEmptyCaches() {
        val cacheIterator = deprecatedCaches.iterator()
        while (cacheIterator.hasNext()) {
            val storedCache = cacheIterator.next()
            if (storedCache.numberOfRecords > 0) {
                continue
            }
            cacheIterator.remove()
            storedCache.close()
            val tapeFile = storedCache.file
            if (!tapeFile.delete()) {
                logger.warn("Cannot remove old DataCache file " + tapeFile + " for topic " + storedCache.readTopic.name)
            }
            val name = tapeFile.absolutePath
            val base = name.substring(0, name.length - CacheStore.TAPE_EXTENSION.length)
            val keySchemaFile = File(base + CacheStore.KEY_SCHEMA_EXTENSION)
            if (!keySchemaFile.delete()) {
                logger.warn("Cannot remove old key schema file " + keySchemaFile + " for topic " + storedCache.readTopic.name)
            }
            val valueSchemaFile = File(base + CacheStore.VALUE_SCHEMA_EXTENSION)
            if (!valueSchemaFile.delete()) {
                logger.warn("Cannot remove old value schema file " + valueSchemaFile + " for topic " + storedCache.readTopic.name)
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        activeDataCache.close()
        deprecatedCaches.forEach(ReadableDataCache::close)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DataCacheGroup::class.java)
    }
}
