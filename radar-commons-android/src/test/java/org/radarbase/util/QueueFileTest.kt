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

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

class QueueFileTest {

    @Rule
    @JvmField
    var folder = TemporaryFolder()

    @Rule
    @JvmField
    var exception: ExpectedException = ExpectedException.none()

    @Test
    @Throws(Exception::class)
    fun elementOutputStream() {
        val queue = createQueue()
        val buffer = ByteArray((MAX_SIZE / 4).toInt())
        queue.elementOutputStream().use { out ->
            out.write(buffer)
            out.next()
            out.write(buffer)
            out.next()
            out.write(buffer)
            out.next()
            exception.expect(IllegalStateException::class.java)
            out.write(buffer)
        }
    }

    @Test
    @Throws(Exception::class)
    fun elementOutputStreamCircular() {
        val queue = createQueue()
        assertEquals(0, queue.size)
        val buffer = ByteArray((MAX_SIZE / 4).toInt())
        queue.elementOutputStream().use { out ->
            out.write(buffer)
            out.next()
            out.write(buffer)
            out.next()
            out.write(buffer)
            out.next()
        }
        assertEquals(3, queue.size)
        queue.remove(2)
        assertEquals(1, queue.size)
        try {
            queue.elementOutputStream().use { out ->
                out.write(buffer)
                out.next()
                out.write(buffer)
                out.next()
                exception.expect(IllegalStateException::class.java)
                out.write(buffer)
            }
        } catch (ex: IOException) {
            logger.info("Queue file cannot be written to {}", queue)
            throw ex
        }

    }

    @Throws(IOException::class)
    private fun createQueue(): QueueFile {
        val file = folder.newFile()
        assertTrue(file.delete())
        return QueueFile.newMapped(file, MAX_SIZE)
    }

    @Test
    @Throws(Exception::class)
    fun isEmpty() {
        val queueFile = createQueue()
        assertTrue(queueFile.isEmpty)
        val value = 1
        queueFile.elementOutputStream().use { out -> out.write(value) }
        assertFalse(queueFile.isEmpty)
        assertEquals(1, queueFile.size)
        val `in` = queueFile.peek()
        assertNotNull(`in`)
        `in`!!.close()
        assertFalse(queueFile.isEmpty)
        assertEquals(1, queueFile.size)
        queueFile.remove(1)
        assertTrue(queueFile.isEmpty)
        assertEquals(0, queueFile.size)
    }

    @Test
    @Throws(Exception::class)
    fun peek() {
        val queueFile = createQueue()
        assertNull(queueFile.peek())
        val random = Random()
        val buffer = ByteArray(16)
        val v1 = random.nextInt(255)
        val v2 = random.nextInt(255)
        random.nextBytes(buffer)
        val expectedBuffer = ByteArray(buffer.size)
        System.arraycopy(buffer, 0, expectedBuffer, 0, buffer.size)
        queueFile.elementOutputStream().use { out ->
            out.write(v1)
            out.next()
            out.write(v2)
            out.next()
            out.write(buffer)
        }
        assertEquals(3, queueFile.size)
        queueFile.peek()!!.use { `in` ->
            assertNotNull(`in`)
            assertEquals(1, `in`.available())
            assertEquals(v1, `in`.read())
        }
        queueFile.peek()!!.use { `in` ->
            assertNotNull(`in`)
            assertEquals(1, `in`.available())
            assertEquals(v1, `in`.read())
        }
        queueFile.remove(1)
        queueFile.peek()!!.use { `in` ->
            assertNotNull(`in`)
            assertEquals(1, `in`.available())
            assertEquals(v2, `in`.read())
        }
        queueFile.remove(1)
        queueFile.peek()!!.use { `in` ->
            assertNotNull(`in`)
            assertEquals(16, `in`.available())
            val actualBuffer = ByteArray(20)
            assertEquals(16, `in`.read(actualBuffer))
            val actualBufferShortened = ByteArray(16)
            System.arraycopy(actualBuffer, 0, actualBufferShortened, 0, 16)
            assertArrayEquals(expectedBuffer, actualBufferShortened)
        }
        queueFile.remove(1)
        assertNull(queueFile.peek())
    }

    @Test
    @Throws(Exception::class)
    operator fun iterator() {
        val queueFile = createQueue()
        assertNull(queueFile.peek())
        queueFile.elementOutputStream().use { out ->
            out.write(1)
            out.next()
            out.write(2)
        }
        val iter = queueFile.iterator()
        assertTrue(iter.hasNext())
        iter.next().use { `in` -> assertEquals(1, `in`.read()) }
        assertTrue(iter.hasNext())
        iter.next().use { `in` -> assertEquals(2, `in`.read()) }
        assertFalse(iter.hasNext())

        exception.expect(NoSuchElementException::class.java)
        iter.next()
    }

    @Test
    @Throws(Exception::class)
    fun clear() {
        val queue = createQueue()
        queue.elementOutputStream().use { out ->
            out.write(1)
            out.next()
            out.write(2)
        }
        assertEquals(2, queue.size)
        queue.clear()
        assertTrue(queue.isEmpty)
    }

    @Throws(IOException::class)
    private fun writeAssertFileSize(expectedSize: Long, expectedUsed: Long, buffer: ByteArray, queue: QueueFile) {
        queue.elementOutputStream().use { out -> out.write(buffer) }
        assertEquals(expectedUsed, queue.usedBytes)
        assertEquals(expectedSize, queue.fileSize)
    }

    @Test
    @Throws(Exception::class)
    fun fileSize() {
        val queue = createQueue()
        assertEquals(MappedQueueFileStorage.MINIMUM_LENGTH, queue.fileSize)
        val bufSize = MAX_SIZE / 16 - QueueFileHeader.QUEUE_HEADER_LENGTH
        val buffer = ByteArray(bufSize.toInt())
        // write buffer, assert that the file size increases with the stored size
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH, bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 2 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 2, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 3 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 2, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 4 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 4, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 5 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 4, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 6 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 4, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 7 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 4, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 8 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 9 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 10 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 11 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 12 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 13 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 14 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 15 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 16 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)

        // queue is full now
        var actualException: Exception? = null
        try {
            writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 17 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        } catch (ex: IllegalStateException) {
            actualException = ex
        }

        assertNotNull(actualException)
        // queue is full, remove elements to add new ones
        queue.remove(1)
        // this buffer is written in a circular way
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 16 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        queue.remove(1)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 16 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        queue.remove(1)
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 16 + QueueFileHeader.QUEUE_HEADER_LENGTH, buffer, queue)
        queue.remove(14)
        assertEquals(2, queue.size)
        assertEquals((bufSize + QueueFileElement.ELEMENT_HEADER_LENGTH) * 2 + QueueFileHeader.QUEUE_HEADER_LENGTH, queue.usedBytes)
        assertEquals(MappedQueueFileStorage.MINIMUM_LENGTH * 2, queue.fileSize)
    }

    @Test(timeout = 10000L)
    @Throws(Throwable::class)
    fun enduranceTest() {
        val numberOfOperations = 1000
        val size = MappedQueueFileStorage.MINIMUM_LENGTH * 2
        val random = Random()
        val buffer = ByteArray((size / 16).toInt())
        val file = folder.newFile()
        assertTrue(file.delete())
        var queue = QueueFile.newMapped(file, size)
        val list = LinkedList<Element>()
        var bytesUsed = 36L

        try {
            for (i in 0 until numberOfOperations) {
                val choice = random.nextDouble()
                if (choice < 0.05) {
                    logger.info("Closing and reopening queue")
                    queue.close()
                    queue = QueueFile.newMapped(file, size)
                } else if (choice < 0.1) {
                    logger.info("Clearing queue")
                    queue.clear()
                    list.clear()
                    bytesUsed = 36
                } else if (choice < 0.325 && !queue.isEmpty) {
                    bytesUsed -= remove(list, queue, random)
                } else if (choice < 0.55 && !queue.isEmpty) {
                    read(list, queue, buffer, random)
                } else {
                    bytesUsed += write(list, queue, buffer, random, size)
                }
                assertEquals(bytesUsed, queue.usedBytes)
                assertEquals(list.size, queue.size)
            }
        } catch (ex: Throwable) {
            logger.error("Current list: {} with used bytes {}; QueueFile {}", list, bytesUsed, queue)
            throw ex
        }

    }

    /**
     * Remove a random number of elements from a queue and a verification list
     * @return bytes removed
     */
    @Throws(IOException::class)
    private fun remove(list: LinkedList<Element>, queue: QueueFile, random: Random): Long {
        val numRemove = random.nextInt(queue.size) + 1
        logger.info("Removing {} elements", numRemove)
        queue.remove(numRemove)
        var removedBytes = 0L
        for (j in 0 until numRemove) {
            removedBytes += list.removeFirst().length + QueueFileElement.ELEMENT_HEADER_LENGTH
        }
        return removedBytes
    }

    /**
     * Read a random number of elements from a queue and a verification list, using given buffer.
     * The sizes read must match the verification list.
     */
    @Throws(Throwable::class)
    private fun read(list: LinkedList<Element>, queue: QueueFile, buffer: ByteArray, random: Random) {
        val numRead = random.nextInt(queue.size) + 1
        assertTrue(queue.size >= numRead)
        logger.info("Reading {} elements", numRead)
        val iterator = queue.iterator()
        for (j in 0 until numRead) {
            val expectedElement = list[j]
            val `in` = iterator.next()
            try {
                var readLength = 0
                var newlyRead = `in`.read(buffer, 0, buffer.size)
                while (newlyRead != -1) {
                    readLength += newlyRead
                    newlyRead = `in`.read(buffer, readLength, buffer.size - readLength)
                }
                assertEquals(expectedElement.length, readLength.toLong())
            } catch (ex: Throwable) {
                logger.error("Inputstream {} of queuefile {} does not match element {}",
                        `in`, queue, expectedElement)
                throw ex
            } finally {
                `in`.close()
            }
        }
    }

    @Throws(IOException::class)
    private fun write(list: LinkedList<Element>, queue: QueueFile, buffer: ByteArray, random: Random, size: Long): Long {
        val numAdd = random.nextInt(16) + 1
        logger.info("Adding {} elements", numAdd)
        var bytesUsed = 0L
        queue.elementOutputStream().use { out ->
            for (j in 0 until numAdd) {
                val numBytes = (random.nextInt(buffer.size) + 1).toLong()
                if (numBytes + out.usedSize + QueueFileElement.ELEMENT_HEADER_LENGTH > size) {
                    logger.info("Not adding to full queue")
                    break
                }
                val next = Element(0, numBytes)
                if (list.isEmpty()) {
                    next.position = QueueFileHeader.QUEUE_HEADER_LENGTH.toLong()
                } else if (out.usedSize + numBytes + QueueFileElement.ELEMENT_HEADER_LENGTH > queue.fileSize) {
                    val firstPosition = list.first.position
                    for (el in list) {
                        if (el.position < firstPosition) {
                            el.position += (queue.fileSize - QueueFileHeader.QUEUE_HEADER_LENGTH).toInt()
                        }
                    }
                    val last = list.last
                    next.position = last.position + last.length + QueueFileElement.ELEMENT_HEADER_LENGTH
                    if (next.position >= queue.fileSize * 2) {
                        next.position += (QueueFileHeader.QUEUE_HEADER_LENGTH - queue.fileSize * 2).toInt()
                    }
                } else {
                    val last = list.last
                    next.position = last.position + last.length + QueueFileElement.ELEMENT_HEADER_LENGTH
                    if (next.position >= queue.fileSize) {
                        next.position += (QueueFileHeader.QUEUE_HEADER_LENGTH - queue.fileSize).toInt()
                    }
                }
                bytesUsed += next.length + QueueFileElement.ELEMENT_HEADER_LENGTH
                list.add(next)
                out.write(buffer, 0, numBytes.toInt())
                out.next()
            }
        }
        return bytesUsed
    }

    data class Element constructor(var position: Long, val length: Long) {
        override fun toString(): String {
            return "[$position, $length]"
        }
    }

    companion object {
        private const val MAX_SIZE = 8 * MappedQueueFileStorage.MINIMUM_LENGTH
        private val logger = LoggerFactory.getLogger(QueueFileTest::class.java)
    }
}
