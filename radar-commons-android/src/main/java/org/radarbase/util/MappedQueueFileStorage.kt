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

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * A storage backend for a QueueFile
 * @author Joris Borgdorff (joris@thehyve.nl)
 *
 * @param file file to use
 * @param initialLength initial length if the file does not exist.
 * @param maximumLength maximum length that the file may have.
 * @throws NullPointerException if file is null
 * @throws IllegalArgumentException if the initialLength or maximumLength is smaller than
 *                                  `QueueFileHeader.ELEMENT_HEADER_LENGTH`.
 * @throws IOException if the file could not be accessed or was smaller than
 *                     `QueueFileHeader.ELEMENT_HEADER_LENGTH`
 */
class MappedQueueFileStorage(file: File, initialLength: Long, maximumLength: Long) : QueueStorage {
    /**
     * The underlying file. Uses a ring buffer to store entries.
     * <pre>
     * Format:
     * QueueFileHeader.ELEMENT_HEADER_LENGTH bytes    Header
     * length bytes                           Data
    </pre> *
     */
    private val channel: FileChannel
    private val randomAccessFile: RandomAccessFile

    /** Filename, for toString purposes  */
    private val fileName: String = file.name

    private var byteBuffer: MappedByteBuffer
    override var isClosed: Boolean = false
        private set

    override val isPreExisting: Boolean = file.exists()

    /** File size in bytes.  */
    override var length: Long = 0L
        private set

    override val minimumLength: Long = MINIMUM_LENGTH

    override var maximumLength: Long = maximumLength
        set(value) {
            require(value <= Int.MAX_VALUE) {
                "Maximum cache size out of range $value <= ${Int.MAX_VALUE}"
            }
            field = value.coerceAtLeast(MINIMUM_LENGTH)
        }

    init {
        require(initialLength >= minimumLength) { "Initial length $initialLength is smaller than minimum length $minimumLength" }
        require(maximumLength <= Int.MAX_VALUE) { "Maximum cache size out of range $maximumLength <= ${Int.MAX_VALUE}" }
        require(initialLength <= maximumLength) { "Initial length $initialLength exceeds maximum length $maximumLength" }

        randomAccessFile = RandomAccessFile(file, "rw")
        length = if (isPreExisting) {
            // Read header from file
            val currentLength = randomAccessFile.length()
            if (currentLength < QueueFileHeader.QUEUE_HEADER_LENGTH) {
                throw IOException("File length " + length + " is smaller than queue header length " + QueueFileHeader.QUEUE_HEADER_LENGTH)
            }
            currentLength
        } else {
            randomAccessFile.setLength(initialLength)
            initialLength
        }
        channel = randomAccessFile.channel
        byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, length)
        byteBuffer.clear()
    }

    @Throws(IOException::class)
    override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Long {
        requireNotClosed()
        checkOffsetAndCount(buffer, offset, count)
        val wrappedPosition = wrapPosition(position)
        byteBuffer.position(wrappedPosition)
        return if (position + count <= length) {
            byteBuffer.get(buffer, offset, count)
            wrapPosition((wrappedPosition + count).toLong()).toLong()
        } else {
            // The read overlaps the EOF.
            // # of bytes to read before the EOF. Guaranteed to be less than Integer.MAX_VALUE.
            val firstPart = (length - wrappedPosition).toInt()
            byteBuffer.get(buffer, offset, firstPart)
            byteBuffer.position(QueueFileHeader.QUEUE_HEADER_LENGTH)
            byteBuffer.get(buffer, offset + firstPart, count - firstPart)
            (QueueFileHeader.QUEUE_HEADER_LENGTH + count - firstPart).toLong()
        }
    }

    /** Wraps the position if it exceeds the end of the file.  */
    private fun wrapPosition(position: Long): Int {
        val newPosition = if (position < length) position else QueueFileHeader.QUEUE_HEADER_LENGTH + position - length
        require(newPosition < length && position >= 0) { "Position $position invalid outside of storage length $length" }
        return newPosition.toInt()
    }

    /** Sets the length of the file.  */
    @Throws(IOException::class)
    override fun resize(size: Long) {
        requireNotClosed()
        if (size == length) {
            return
        }
        require(!(size > length && size > maximumLength)) {
            "New length $size exceeds maximum length $maximumLength"
        }
        require(size >= MINIMUM_LENGTH) {
            "New length $size is less than minimum length ${QueueFileHeader.QUEUE_HEADER_LENGTH}"
        }
        flush()
        randomAccessFile.setLength(size)
        channel.force(true)
        byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, size)
        length = size
    }

    override fun flush() {
        byteBuffer.force()
    }

    @Throws(IOException::class)
    override fun write(position: Long, buffer: ByteArray, offset: Int, count: Int): Long {
        requireNotClosed()
        checkOffsetAndCount(buffer, offset, count)
        val wrappedPosition = wrapPosition(position)

        byteBuffer.position(wrappedPosition)
        val linearPart = (length - wrappedPosition).toInt()
        return if (linearPart >= count) {
            byteBuffer.put(buffer, offset, count)
            wrapPosition((wrappedPosition + count).toLong()).toLong()
        } else {
            // The write overlaps the EOF.
            // # of bytes to write before the EOF. Guaranteed to be less than Integer.MAX_VALUE.
            if (linearPart > 0) {
                byteBuffer.put(buffer, offset, linearPart)
            }
            byteBuffer.position(QueueFileHeader.QUEUE_HEADER_LENGTH)
            byteBuffer.put(buffer, offset + linearPart, count - linearPart)
            (QueueFileHeader.QUEUE_HEADER_LENGTH + count - linearPart).toLong()
        }
    }

    @Throws(IOException::class)
    override fun move(srcPosition: Long, dstPosition: Long, count: Long) {
        requireNotClosed()
        require(srcPosition >= 0
                && dstPosition >= 0
                && count > 0
                && srcPosition + count <= length
                && dstPosition + count <= length) {
            "Movement specification src=$srcPosition, count=$count, dst=$dstPosition is invalid for storage of length $length"
        }
        flush()
        channel.position(dstPosition)

        if (channel.transferTo(srcPosition, count, channel) != count) {
            throw IOException("Cannot move all data")
        }
    }

    @Throws(IOException::class)
    private fun requireNotClosed() {
        if (isClosed) {
            throw IOException("closed")
        }
    }

    @Throws(IOException::class)
    override fun close() {
        isClosed = true
        channel.close()
        randomAccessFile.close()
    }

    override fun toString(): String {
        return "MappedQueueFileStorage<$fileName>[length=$length]"
    }

    private fun checkOffsetAndCount(bytes: ByteArray, offset: Int, count: Int) {
        if (offset < 0) {
            throw IndexOutOfBoundsException("offset < 0")
        }
        if (count < 0) {
            throw IndexOutOfBoundsException("count < 0")
        }
        require(count + QueueFileHeader.QUEUE_HEADER_LENGTH <= length) {
            "buffer count $count exceeds storage length $length"
        }
        if (offset + count > bytes.size) {
            throw IndexOutOfBoundsException(
                    "extent of offset and length larger than buffer length")
        }
    }

    companion object {
        /** Initial file size in bytes.  */
        const val MINIMUM_LENGTH = 4096L // one file system block
    }
}
