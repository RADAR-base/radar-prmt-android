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

import org.radarbase.util.Serialization.intToBytes
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.OutputStream
import java.util.*

/**
 * An OutputStream that can write multiple elements. After finished writing one element, call
 * [.next] to start writing the next.
 *
 *
 * It is very important to [.close] this OutputStream, as this is the only way that the
 * data is actually committed to file.
 */
class QueueFileOutputStream internal constructor(
        private val queue: QueueFile,
        private val header: QueueFileHeader,
        private val storage: QueueStorage,
        position: Long) : OutputStream() {

    private val current: QueueFileElement
    private var isClosed: Boolean = false
    private val newLast: QueueFileElement
    private val newFirst: QueueFileElement
    private var elementsWritten: Int = 0
    private var streamBytesUsed: Long = 0
    private val elementHeaderBuffer = ByteArray(QueueFileElement.ELEMENT_HEADER_LENGTH)
    private val singleByteBuffer = ByteArray(1)
    private var storagePosition: Long = 0

    init {
        this.storagePosition = header.wrapPosition(position)
        this.current = QueueFileElement(storagePosition, 0)
        isClosed = false
        newLast = QueueFileElement()
        newFirst = QueueFileElement()
        elementsWritten = 0
        streamBytesUsed = 0L
    }

    @Throws(IOException::class)
    override fun write(byteValue: Int) {
        singleByteBuffer[0] = (byteValue and 0xFF).toByte()
        write(singleByteBuffer, 0, 1)
    }

    @Throws(IOException::class)
    override fun write(bytes: ByteArray, offset: Int, count: Int) {
        QueueFile.checkOffsetAndCount(bytes, offset, count)
        if (count == 0) {
            return
        }
        checkConditions()

        if (current.isEmpty) {
            expandAndUpdate(QueueFileElement.ELEMENT_HEADER_LENGTH + count.toLong())
            Arrays.fill(elementHeaderBuffer, 0.toByte())
            storagePosition = storage.write(storagePosition, elementHeaderBuffer, 0, QueueFileElement.ELEMENT_HEADER_LENGTH)
        } else {
            expandAndUpdate(count.toLong())
        }

        storagePosition = storage.write(storagePosition, bytes, offset, count)
        current.length += count
    }

    @Throws(IOException::class)
    private fun checkConditions() {
        if (isClosed || storage.isClosed) {
            throw IOException("isClosed")
        }
    }

    /**
     * Proceed writing the next element. Zero length elements are not written, so always write
     * at least one byte to store an element.
     * @throws IOException if the QueueFileStorage cannot be written to
     */
    @Throws(IOException::class)
    operator fun next() {
        checkConditions()
        if (current.isEmpty) {
            return
        }
        newLast.update(current)
        if (newFirst.isEmpty && queue.isEmpty) {
            newFirst.update(current)
        }

        current.position = storagePosition
        current.length = 0

        intToBytes(newLast.length, elementHeaderBuffer, 0)
        elementHeaderBuffer[4] = newLast.crc
        storage.write(newLast.position, elementHeaderBuffer, 0, QueueFileElement.ELEMENT_HEADER_LENGTH)

        elementsWritten++
    }

    /**
     * Size of the storage that will be used if the OutputStream is closed.
     * @return number of bytes used
     */
    internal val usedSize: Long
        get() = queue.usedBytes + streamBytesUsed

    /**
     * Expands the storage if necessary, updating the queue length if needed.
     * @throws IllegalStateException if the queue is full.
     */
    @Throws(IOException::class)
    private fun expandAndUpdate(length: Long) {
        val newStreamBytesUsed = streamBytesUsed + length
        val bytesNeeded = queue.usedBytes + newStreamBytesUsed

        if (bytesNeeded > queue.maximumFileSize) {
            // reset current element
            current.length = 0
            throw IllegalStateException("Data does not fit in queue")
        }

        streamBytesUsed = newStreamBytesUsed
        val oldLength = header.length
        if (bytesNeeded <= oldLength) {
            return
        }

        logger.debug("Extending {}", queue)

        // Double the length until we can fit the new data.
        var newLength = oldLength * 2
        while (newLength < bytesNeeded) {
            newLength += newLength
        }

        val beginningOfFirstElement = if (!newFirst.isEmpty) {
            newFirst.position
        } else {
            header.firstPosition
        }

        queue.setFileLength(Math.min(queue.maximumFileSize, newLength), storagePosition, beginningOfFirstElement)

        if (storagePosition <= beginningOfFirstElement) {
            val positionUpdate = oldLength - QueueFileHeader.QUEUE_HEADER_LENGTH

            if (current.position <= beginningOfFirstElement) {
                current.position = current.position + positionUpdate
            }
            storagePosition += positionUpdate
        }
    }

    /**
     * Closes the stream and commits it to file.
     * @throws IOException if the output stream cannot be written to.
     */
    @Throws(IOException::class)
    override fun close() {
        try {
            next()
            if (elementsWritten > 0) {
                queue.commitOutputStream(newFirst, newLast, elementsWritten)
            }
        } finally {
            isClosed = true
        }
    }

    override fun toString(): String {
        return ("QueueFileOutputStream[current=" + current
                + ",total=" + streamBytesUsed + ",used=" + usedSize + "]")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(QueueFileOutputStream::class.java)
    }
}
