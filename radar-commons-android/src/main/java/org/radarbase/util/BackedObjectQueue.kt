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

package org.radarbase.util

//import com.crashlytics.android.Crashlytics
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/**
 * A queue-like object queue that is backed by a file storage.
 * @param <S> type of objects to store.
 * @param <T> type of objects to retrieve.
 * @param queueFile file to write objects to
 * @param serializer way to serialize from given objects
 * @param deserializer way to deserialize to objects from a stream
 */
class BackedObjectQueue<S, T>(
        private val queueFile: QueueFile,
        private val serializer: Serializer<S>,
        private val deserializer: Deserializer<T>) : Closeable {

    /** Returns `true` if this queue contains no entries.  */
    val isEmpty: Boolean
        get() = size == 0

    /** Number of elements in the queue.  */
    val size: Int
        get() = queueFile.size

    /**
     * Add a new element to the queue.
     * @param entry element to add
     * @throws IOException if the backing file cannot be accessed, or the element
     * cannot be converted.
     * @throws IllegalArgumentException if given entry is not a valid object for serialization.
     * @throws IllegalStateException if the queue is full
     */
    @Throws(IOException::class)
    fun add(entry: S) {
        queueFile.elementOutputStream().use { out -> serializer.serialize(entry, out) }
    }

    /**
     * Add a collection of new element to the queue.
     * @param entries elements to add
     * @throws IOException if the backing file cannot be accessed or the element
     * cannot be converted.
     * @throws IllegalArgumentException if given entry is not a valid object for serialization.
     * @throws IllegalStateException if the queue is full
     */
    @Throws(IOException::class)
    fun addAll(entries: Collection<S>) {
        queueFile.elementOutputStream().use { out ->
            for (entry in entries) {
                serializer.serialize(entry, out)
                out.next()
            }
        }
    }

    /**
     * Get the front-most object in the queue. This does not remove the element.
     * @return front-most element or null if none is available
     * @throws IOException if the element could not be read or deserialized
     * @throws IllegalStateException if the element that was read was invalid.
     */
    @Throws(IOException::class)
    fun peek(): T? {
        return queueFile.peek()
                ?.use { deserializer.deserialize(it) }
    }

    /**
     * Get at most `n` front-most objects in the queue. This does not remove the elements.
     * Elements that were found to be invalid according to the current schema are logged. This
     * method will try to read at least one record. After that, no more than `n` records are
     * read, and their collective serialized size is no larger than `sizeLimit`.
     * @param n number of elements to retrieve at most.
     * @param sizeLimit limit for the size of read data.
     * @return list of elements, with at most `n` elements.
     * @throws IOException if the element could not be read or deserialized
     * @throws IllegalStateException if the element could not be read
     */
    @Throws(IOException::class)
    fun peek(n: Int, sizeLimit: Long): List<T?> {
        val iter = queueFile.iterator()
        var curSize: Long = 0
        val results = ArrayList<T?>(n)
        var i = 0
        while (i < n && iter.hasNext() && curSize < sizeLimit) {
            iter.next().use { input ->
                curSize += input.available().toLong()
                if (curSize <= sizeLimit || i == 0) {
                    try {
                        results.add(deserializer.deserialize(input))
                    } catch (ex: IllegalStateException) {
                        //Crashlytics.logException(ex)
                        logger.warn("Invalid record ignored: {}", ex.message)
                        results.add(null)
                    }
                }
            }
            i++
        }
        return results
    }

    /**
     * Remove the first `n` elements from the queue.
     *
     * @throws IOException when the elements could not be removed
     * @throws NoSuchElementException if more than the available elements are requested to be removed
     */
    @Throws(IOException::class)
    @JvmOverloads
    fun remove(n: Int = 1) {
        queueFile.remove(n)
    }

    /**
     * Close the queue. This also closes the backing file.
     * @throws IOException if the file cannot be closed.
     */
    @Throws(IOException::class)
    override fun close() {
        queueFile.close()
    }

    /** Converts objects into streams.  */
    interface Serializer<S> {
        /**
         * Serialize an object to given output stream.
         * @param output output, which will not be closed after this call.
         * @throws IOException if a valid object could not be serialized to the stream
         * @throws IllegalStateException if the underlying queue is full.
         */
        @Throws(IOException::class)
        fun serialize(value: S, output: OutputStream)
    }

    /** Converts streams into objects.  */
    interface Deserializer<T> {
        /**
         * Deserialize an object from given input stream.
         * @param `input` input, which will not be closed after this call.
         * @return deserialized object
         * @throws IOException if a valid object could not be deserialized from the stream
         */
        @Throws(IOException::class)
        fun deserialize(input: InputStream): T
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BackedObjectQueue::class.java)
    }
}
