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

import org.radarbase.util.Serialization.bytesToInt
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * An efficient, file-based, FIFO queue. Additions and removals are O(1). Writes are
 * synchronous; data will be written to disk before an operation returns.
 * The underlying file is structured to survive process and even system crashes. If an I/O
 * exception is thrown during a mutating change, the change is aborted. It is safe to continue to
 * use a `QueueFile` instance after an exception.
 *
 *
 * **Note that this implementation is not synchronized.**
 *
 *
 * In a traditional queue, the remove operation returns an element. In this queue,
 * [.peek] and [.remove] are used in conjunction. Use
 * `peek` to retrieve the first element, and then `remove` to remove it after
 * successful processing. If the system crashes after `peek` and during processing, the
 * element will remain in the queue, to be processed when the system restarts.
 *
 * This class is an adaptation of com.squareup.tape2, allowing multi-element writes. It also
 * removes legacy support.
 *
 * @author Bob Lee (bob@squareup.com)
 * @author Joris Borgdorff (joris@thehyve.nl)
 */
class QueueFile @Throws(IOException::class)
constructor(private val storage: QueueStorage) : Closeable, Iterable<InputStream> {

    /**
     * The underlying file. Uses a ring buffer to store entries. Designed so that a modification
     * isn't committed or visible until we write the header. The header is much smaller than a
     * segment. Storing the file length ensures we can recover from a failed expansion
     * (i.e. if setting the file length succeeds but the process dies before the data can be
     * copied).
     * <pre>
     * Format:
     * 36 bytes         Header
     * ...              Data
     *
     * Header:
     * 4 bytes          Version
     * 8 bytes          File length
     * 4 bytes          Element count
     * 8 bytes          Head element position
     * 8 bytes          Tail element position
     * 4 bytes          Header checksum
     *
     * Element:
     * 4 bytes          Data length `n`
     * 1 byte           Element header length checksum
     * `n` bytes          Data
    </pre> *
     */
    private val header: QueueFileHeader = QueueFileHeader(storage)

    /** Pointer to first (or eldest) element.  */
    private val first = ArrayDeque<QueueFileElement>()

    /** Pointer to last (or newest) element.  */
    private val last: QueueFileElement

    /**
     * The number of times this file has been structurally modified - it is incremented during
     * [.remove] and [.elementOutputStream]. Used by [ElementIterator]
     * to guard against concurrent modification.
     */
    private val modCount = AtomicInteger(0)

    private val elementHeaderBuffer = ByteArray(QueueFileElement.ELEMENT_HEADER_LENGTH)

    /** Returns true if this queue contains no entries.  */
    val isEmpty: Boolean
        get() = size == 0

    var maximumFileSize: Long
        get() = storage.maximumLength
        set(newSize) {
            storage.maximumLength = newSize
        }

    init {
        if (header.length < storage.length) {
            this.storage.resize(header.length)
        }

        readElement(header.firstPosition).takeUnless { it.isEmpty }
                ?.let { first.add(it) }

        last = readElement(header.lastPosition)
    }

    /**
     * Read element header data into given element.
     *
     * @param position position of the element
     * @param elementToUpdate element to update with found information
     * @throws IOException if the header is incorrect and so the file is corrupt.
     */
    @Throws(IOException::class)
    private fun readElement(position: Long, elementToUpdate: QueueFileElement) {
        if (position == 0L) {
            elementToUpdate.reset()
            return
        }

        storage.read(position, elementHeaderBuffer, 0, QueueFileElement.ELEMENT_HEADER_LENGTH)
        val length = bytesToInt(elementHeaderBuffer, 0)

        if (elementHeaderBuffer[4] != QueueFileElement.crc(length)) {
            logger.error("Failed to verify {}: crc {} does not match stored checksum {}. " + "QueueFile is corrupt.",
                    elementToUpdate, QueueFileElement.crc(length), elementHeaderBuffer[4])
            close()
            throw IOException("Element is not correct; queue file is corrupted")
        }
        elementToUpdate.length = length
        elementToUpdate.position = position
    }

    /**
     * Read a new element.
     *
     * @param position position of the element header
     * @return element with information found at position
     * @throws IOException if the header is incorrect and so the file is corrupt.
     */
    @Throws(IOException::class)
    private fun readElement(position: Long): QueueFileElement {
        return QueueFileElement().apply {
            readElement(position, this)
        }
    }

    /**
     * Adds an element to the end of the queue.
     */
    @Throws(IOException::class)
    fun elementOutputStream(): QueueFileOutputStream {
        requireNotClosed()
        return QueueFileOutputStream(this, header, storage, last.nextPosition)
    }

    /** Number of bytes used in the file.  */
    val usedBytes: Long
        get() {
            if (isEmpty) {
                return QueueFileHeader.QUEUE_HEADER_LENGTH.toLong()
            }

            val firstPosition = first.first.position
            return last.nextPosition - firstPosition + if (last.position >= firstPosition) {
                QueueFileHeader.QUEUE_HEADER_LENGTH.toLong()
            } else {
                // tail < head. The queue wraps.
                header.length
            }
        }

    /** Returns an InputStream to read the eldest element. Returns null if the queue is empty.  */
    @Throws(IOException::class)
    fun peek(): InputStream? {
        requireNotClosed()
        return if (!isEmpty) QueueFileInputStream(first.first) else null
    }

    /**
     * Returns an iterator over elements in this QueueFile.
     *
     *
     * The iterator disallows modifications to be made to the QueueFile during iteration.
     */
    override fun iterator(): Iterator<InputStream> = ElementIterator()

    internal inner class ElementIterator : Iterator<InputStream> {
        /** Index of element to be returned by subsequent call to next.  */
        private var nextElementIndex: Int = 0

        /** Position of element to be returned by subsequent call to next.  */
        private var nextElementPosition: Long = if (first.isEmpty()) 0 else first.first.position

        /**
         * The [.modCount] value that the iterator believes that the backing QueueFile should
         * have. If this expectation is violated, the iterator has detected concurrent modification.
         */
        private val expectedModCount = modCount.get()

        private var cacheIterator: Iterator<QueueFileElement>? = first.iterator()
        private var previousCached: QueueFileElement? = QueueFileElement()

        private fun checkConditions() {
            check(!storage.isClosed) { "storage is closed" }
            if (modCount.get() != expectedModCount) {
                throw ConcurrentModificationException()
            }
        }

        override fun hasNext(): Boolean {
            checkConditions()
            return nextElementIndex != header.count
        }

        override fun next(): InputStream {
            checkConditions()
            if (nextElementIndex >= header.count) {
                throw NoSuchElementException()
            }

            val current: QueueFileElement
            if (cacheIterator?.hasNext() == true) {
                current = cacheIterator!!.next()
                current.updateIfMoved(previousCached!!, header)
                previousCached!!.update(current)
            } else {
                if (cacheIterator != null) {
                    cacheIterator = null
                    previousCached = null
                }
                try {
                    current = readElement(nextElementPosition)
                } catch (ex: IOException) {
                    throw IllegalStateException("Cannot read element", ex)
                }

                first.add(current)
            }
            val input = QueueFileInputStream(current)

            // Update the pointer to the next element.
            nextElementPosition = header.wrapPosition(current.nextPosition)
            nextElementIndex++

            // Return the read element.
            return input
        }

        override fun toString(): String {
            return "QueueFile[position=$nextElementPosition, index=$nextElementIndex]"
        }
    }

    /** Returns the number of elements in this queue.  */
    val size: Int
        get() = header.count

    /** File size in bytes  */
    val fileSize: Long
        get() = header.length

    /**
     * Removes the eldest `n` elements.
     *
     * @throws NoSuchElementException if more than the available elements are requested to be removed
     */
    @Throws(IOException::class)
    fun remove(n: Int) {
        requireNotClosed()
        require(n >= 0) { "Cannot remove negative ($n) number of elements." }
        if (n == 0) {
            return
        }
        if (n == header.count) {
            clear()
            return
        }
        if (n > header.count) {
            throw NoSuchElementException(
                    "Cannot remove more elements (" + n + ") than present in queue (" + header.count + ").")
        }

        // Read the position and length of the new first element.
        var newFirst = QueueFileElement()
        val previous = QueueFileElement()

        var i = 0
        // remove from cache first
        while (i < n && !first.isEmpty()) {
            newFirst.update(first.removeFirst())
            newFirst.updateIfMoved(previous, header)
            previous.update(newFirst)
            i++
        }

        if (first.isEmpty()) {
            // if the cache contained less than n elements, skip from file
            // read one additional element to become the first element of the cache.
            while (i <= n) {
                readElement(header.wrapPosition(newFirst.nextPosition), newFirst)
                i++
            }
            // the next element was read from file and will become the next first element
            first.add(newFirst)
        } else {
            newFirst = first.first
            newFirst.updateIfMoved(previous, header)
        }

        // Commit the header.
        modCount.incrementAndGet()
        header.firstPosition = newFirst.position
        header.addCount(-n)
        truncateIfNeeded()
        header.write()
    }

    /**
     * Truncate file if a lot of space is empty and no copy operations are needed.
     */
    @Throws(IOException::class)
    private fun truncateIfNeeded() {
        if (header.lastPosition >= header.firstPosition && last.nextPosition <= maximumFileSize) {
            var newLength = header.length
            var goalLength = newLength / 2
            val bytesUsed = usedBytes
            val maxExtent = last.nextPosition

            while (goalLength >= storage.minimumLength
                    && maxExtent <= goalLength
                    && bytesUsed <= goalLength / 2) {
                newLength = goalLength
                goalLength /= 2
            }
            if (newLength < header.length) {
                logger.debug("Truncating {} from {} to {}", this, header.length, newLength)
                storage.resize(newLength)
                header.length = newLength
            }
        }
    }

    /** Clears this queue. Truncates the file to the initial size.  */
    @Throws(IOException::class)
    fun clear() {
        requireNotClosed()

        first.clear()
        last.reset()
        header.clear()

        if (header.length != storage.minimumLength) {
            storage.resize(storage.minimumLength)
            header.length = storage.minimumLength
        }

        header.write()

        modCount.incrementAndGet()
    }

    @Throws(IOException::class)
    private fun requireNotClosed() {
        if (storage.isClosed) {
            throw IOException("closed")
        }
    }

    @Throws(IOException::class)
    override fun close() {
        storage.close()
    }

    override fun toString(): String {
        return "QueueFile[storage=$storage, header=$header, first=$first, last=$last]"
    }

    private inner class QueueFileInputStream(element: QueueFileElement) : InputStream() {
        private val totalLength: Int
        private val expectedModCount: Int
        private val singleByteArray = ByteArray(1)
        private var storagePosition: Long = 0
        private var bytesRead: Int = 0

        init {
            this.storagePosition = header.wrapPosition(element.dataPosition)
            this.totalLength = element.length
            this.expectedModCount = modCount.get()
            this.bytesRead = 0
        }

        override fun available(): Int {
            return totalLength - bytesRead
        }

        override fun skip(byteCount: Long): Long {
            val countAvailable = Math.min(byteCount, (totalLength - bytesRead).toLong()).toInt()
            bytesRead += countAvailable
            storagePosition = header.wrapPosition(storagePosition + countAvailable)
            return countAvailable.toLong()
        }

        @Throws(IOException::class)
        override fun read(): Int {
            if (read(singleByteArray, 0, 1) != 1) {
                throw IOException("Cannot read byte")
            }
            return singleByteArray[0].toInt() and 0xFF
        }

        @Throws(IOException::class)
        override fun read(bytes: ByteArray, offset: Int, count: Int): Int {
            if (bytesRead == totalLength) {
                return -1
            }
            if (count < 0) {
                throw IndexOutOfBoundsException("length < 0")
            }
            if (count == 0) {
                return 0
            }
            checkForCoModification()

            val countAvailable = Math.min(count, totalLength - bytesRead)
            storagePosition = storage.read(storagePosition, bytes, offset, countAvailable)

            bytesRead += countAvailable
            return countAvailable
        }

        @Throws(IOException::class)
        private fun checkForCoModification() {
            if (modCount.get() != expectedModCount) {
                throw IOException("Buffer modified while reading InputStream")
            }
        }

        override fun toString(): String {
            return "QueueFileInputStream[length=$totalLength,bytesRead=$bytesRead]"
        }
    }

    @Throws(IOException::class)
    internal fun commitOutputStream(newFirst: QueueFileElement, newLast: QueueFileElement, count: Int) {
        if (!newLast.isEmpty) {
            last.update(newLast)
            header.lastPosition = newLast.position
        }
        if (!newFirst.isEmpty && first.isEmpty()) {
            first.add(newFirst)
            header.firstPosition = newFirst.position
        }
        storage.flush()
        header.addCount(count)
        header.write()
        modCount.incrementAndGet()
    }

    @Suppress("ConvertTwoComparisonsToRangeCheck")
    @Throws(IOException::class)
    fun setFileLength(size: Long, position: Long, beginningOfFirstElement: Long) {
        require(size <= maximumFileSize) { "File length may not exceed maximum file length" }
        val oldLength = header.length
        require(size >= oldLength) { "File length may not be decreased" }
        require(beginningOfFirstElement < oldLength && beginningOfFirstElement >= 0) { "First element may not exceed file size" }
        require(position <= oldLength && position >= 0) { "Position may not exceed file size" }

        storage.resize(size)
        header.length = size

        // Calculate the position of the tail end of the data in the ring buffer
        // If the buffer is split, we need to make it contiguous
        if (position <= beginningOfFirstElement) {
            if (position > QueueFileHeader.QUEUE_HEADER_LENGTH) {
                val count = position - QueueFileHeader.QUEUE_HEADER_LENGTH
                storage.move(QueueFileHeader.QUEUE_HEADER_LENGTH.toLong(), oldLength, count)
            }
            modCount.incrementAndGet()

            // Last position was moved forward in the copy
            val positionUpdate = oldLength - QueueFileHeader.QUEUE_HEADER_LENGTH
            if (header.lastPosition < beginningOfFirstElement) {
                header.lastPosition = header.lastPosition + positionUpdate
                last.position = header.lastPosition
            }
        }

        header.write()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(QueueFile::class.java)

        @Throws(IOException::class)
        fun newMapped(file: File, maxSize: Long): QueueFile {
            return QueueFile(MappedQueueFileStorage(
                    file, MappedQueueFileStorage.MINIMUM_LENGTH, maxSize))
        }

        fun checkOffsetAndCount(bytes: ByteArray, offset: Int, length: Int) {
            if (offset < 0) {
                throw IndexOutOfBoundsException("offset < 0")
            }
            if (length < 0) {
                throw IndexOutOfBoundsException("length < 0")
            }
            if (offset + length > bytes.size) {
                throw IndexOutOfBoundsException(
                        "extent of offset and length larger than buffer length")
            }
        }
    }
}
