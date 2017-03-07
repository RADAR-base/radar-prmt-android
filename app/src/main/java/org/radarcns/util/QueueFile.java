/*
 * Copyright (C) 2010 Square, Inc.
 * Copyright (C) 2017 The Hyve
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.radarcns.util;

import android.support.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * An efficient, file-based, FIFO queue. Additions and removals are O(1). Writes are
 * synchronous; data will be written to disk before an operation returns.
 * The underlying file is structured to survive process and even system crashes. If an I/O
 * exception is thrown during a mutating change, the change is aborted. It is safe to continue to
 * use a {@code QueueFile} instance after an exception.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 *
 * <p>In a traditional queue, the remove operation returns an element. In this queue,
 * {@link #peek} and {@link #remove} are used in conjunction. Use
 * {@code peek} to retrieve the first element, and then {@code remove} to remove it after
 * successful processing. If the system crashes after {@code peek} and during processing, the
 * element will remain in the queue, to be processed when the system restarts.
 *
 * This class is an adaptation of com.squareup.tape2, allowing multi-element writes. It also
 * removes legacy support.
 *
 * @author Bob Lee (bob@squareup.com)
 * @author Joris Borgdorff (joris@thehyve.nl)
 */
public final class QueueFile implements Closeable, Iterable<InputStream> {
    private static final Logger logger = LoggerFactory.getLogger(QueueFile.class);

    /**
     * The underlying file. Uses a ring buffer to store entries. Designed so that a modification
     * isn't committed or visible until we write the header. The header is much smaller than a
     * segment. Storing the file length ensures we can recover from a failed expansion
     * (i.e. if setting the file length succeeds but the process dies before the data can be
     * copied).
     * <pre>
     * Format:
     *   36 bytes      Header
     *   ...              Data
     *
     * Header (32 bytes):
     *   4 bytes          Version
     *   8 bytes          File length
     *   4 bytes          Element count
     *   8 bytes          Head element position
     *   8 bytes          Tail element position
     *   4 bytes          Header checksum
     *
     * Element:
     *   4 bytes          Data length `n`
     *   4 bytes          Element header checksum
     *   `n` bytes          Data
     * </pre>
     */
    private final QueueFileHeader header;

    /** Pointer to first (or eldest) element. */
    private final LinkedList<QueueFileElement> first;

    /** Pointer to last (or newest) element. */
    private final QueueFileElement last;

    private final QueueStorage storage;

    /**
     * The number of times this file has been structurally modified — it is incremented during
     * {@link #remove(int)} and {@link #elementOutputStream()}. Used by {@link ElementIterator}
     * to guard against concurrent modification.
     */
    private int modCount = 0;

    private final byte[] elementHeaderBuffer = new byte[QueueFileElement.HEADER_LENGTH];

    public QueueFile(QueueStorage storage) throws IOException {
        this.storage = storage;
        this.header = new QueueFileHeader(storage);

        if (header.getLength() < storage.length()) {
            this.storage.resize(header.getLength());
        }

        first = new LinkedList<>();
        QueueFileElement newFirst = readElement((int)header.getFirstPosition());
        if (!newFirst.isEmpty()) {
            first.add(newFirst);
        }
        last = readElement((int)header.getLastPosition());
    }

    public static QueueFile newMapped(File file, int maxSize) throws IOException {
        return new QueueFile(new MappedQueueFileStorage(
                file, MappedQueueFileStorage.MINIMUM_LENGTH, maxSize));
    }

    /**
     * Read element header data into given element.
     *
     * @param position position of the element
     * @param elementToUpdate element to update with found information
     * @throws IOException if the header is incorrect and so the file is corrupt.
     */
    private void readElement(int position, QueueFileElement elementToUpdate) throws IOException {
        if (position == 0) {
            elementToUpdate.reset();
            return;
        }

        storage.read(position, elementHeaderBuffer, 0, QueueFileElement.HEADER_LENGTH);
        int length = bytesToInt(elementHeaderBuffer, 0);

        if (elementHeaderBuffer[4] != QueueFileElement.crc(length)) {
            logger.error("Failed to verify {}: crc {} does not match stored checksum {}. "
                    + "QueueFile is corrupt.",
                    elementToUpdate, QueueFileElement.crc(length), elementHeaderBuffer[4]);
            close();
            throw new IOException("Element is not correct; queue file is corrupted");
        }
        elementToUpdate.setLength(length);
        elementToUpdate.setPosition(position);
    }

    /**
     * Read a new element.
     *
     * @param position position of the element header
     * @return element with information found at position
     * @throws IOException if the header is incorrect and so the file is corrupt.
     */
    private QueueFileElement readElement(int position) throws IOException {
        QueueFileElement result = new QueueFileElement();
        readElement(position, result);
        return result;
    }

    /**
     * Adds an element to the end of the queue.
     */
    public QueueFileOutputStream elementOutputStream() throws IOException {
        requireNotClosed();
        return new QueueFileOutputStream(this, header, storage, last.nextPosition());
    }

    /** Number of bytes used in the file. */
    public long usedBytes() {
        if (isEmpty()) {
            return QueueFileHeader.HEADER_LENGTH;
        }

        long firstPosition = first.getFirst().getPosition();
        if (last.getPosition() >= firstPosition) {
            // Contiguous queue.
            return last.nextPosition() - firstPosition + QueueFileHeader.HEADER_LENGTH;
        } else {
            // tail < head. The queue wraps.
            return last.nextPosition() - firstPosition + header.getLength();
        }
    }

    /** Returns true if this queue contains no entries. */
    public boolean isEmpty() {
        return size() == 0;
    }

    /** Returns an InputStream to read the eldest element. Returns null if the queue is empty. */
    public InputStream peek() throws IOException {
        requireNotClosed();
        if (isEmpty()) {
            return null;
        }
        return new QueueFileInputStream(first.getFirst());
    }

    /**
     * Returns an iterator over elements in this QueueFile.
     *
     * <p>The iterator disallows modifications to be made to the QueueFile during iteration. Removing
     * elements from the head of the QueueFile is permitted during iteration using
     * {@link Iterator#remove()}.
     *
     * <p>The iterator may throw an unchecked {@link RuntimeException} {@link Iterator#remove()}.
     */
    @Override
    public Iterator<InputStream> iterator() {
        return new ElementIterator();
    }

    private final class ElementIterator implements Iterator<InputStream> {
        /** Index of element to be returned by subsequent call to next. */
        private int nextElementIndex;

        /** Position of element to be returned by subsequent call to next. */
        private long nextElementPosition;

        /**
         * The {@link #modCount} value that the iterator believes that the backing QueueFile should
         * have. If this expectation is violated, the iterator has detected concurrent modification.
         */
        private int expectedModCount;

        private Iterator<QueueFileElement> cacheIterator;
        private QueueFileElement previousCached;

        private ElementIterator() {
            nextElementIndex = 0;
            expectedModCount = modCount;
            previousCached = new QueueFileElement();
            cacheIterator = first.iterator();
            nextElementPosition = first.isEmpty() ? 0 : first.getFirst().getPosition();
        }

        private void checkForComodification() {
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
        }

        @Override
        public boolean hasNext() {
            if (storage.isClosed()) {
                throw new IllegalStateException("closed");
            }
            checkForComodification();
            return nextElementIndex != header.getCount();
        }

        @Override
        public InputStream next() {
            if (storage.isClosed()) {
                throw new IllegalStateException("closed");
            }
            checkForComodification();
            if (nextElementIndex >= header.getCount()) {
                throw new NoSuchElementException();
            }

            QueueFileElement current;
            if (cacheIterator != null && cacheIterator.hasNext()) {
                current = cacheIterator.next();
                current.updateIfMoved(previousCached, header);
                previousCached.update(current);
            } else {
                if (cacheIterator != null) {
                    cacheIterator = null;
                    previousCached = null;
                }
                try {
                    current = readElement((int)nextElementPosition);
                } catch (IOException ex) {
                    throw new IllegalStateException("Cannot read element", ex);
                }
                first.add(current);
            }
            InputStream in = new QueueFileInputStream(current);

            // Update the pointer to the next element.
            nextElementPosition = (int)header.wrapPosition(current.nextPosition());
            nextElementIndex++;

            // Return the read element.
            return in;
        }

        /** Removal is not supported */
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "QueueFile[position=" + nextElementPosition + ", index=" + nextElementIndex + "]";
        }
    }

    /** Returns the number of elements in this queue. */
    public int size() {
        return header.getCount();
    }

    /** File size in bytes */
    public long fileSize() {
        return header.getLength();
    }

    /**
     * Removes the eldest {@code n} elements.
     *
     * @throws NoSuchElementException if more than the available elements are requested to be removed
     */
    public void remove(int n) throws IOException {
        requireNotClosed();
        if (n < 0) {
            throw new IllegalArgumentException("Cannot remove negative (" + n + ") number of elements.");
        }
        if (n == 0) {
            return;
        }
        if (n == header.getCount()) {
            clear();
            return;
        }
        if (n > header.getCount()) {
            throw new NoSuchElementException(
                    "Cannot remove more elements (" + n + ") than present in queue (" + header.getCount() + ").");
        }

        // Read the position and length of the new first element.
        QueueFileElement newFirst = new QueueFileElement();
        QueueFileElement previous = new QueueFileElement();
        int i;
        // remove from cache first
        for (i = 0; i < n && !first.isEmpty(); i++) {
            newFirst.update(first.removeFirst());
            newFirst.updateIfMoved(previous, header);
            previous.update(newFirst);
        }

        if (first.isEmpty()) {
            // if the cache contained less than n elements, skip from file
            // read one additional element to become the first element of the cache.
            for (; i <= n; i++) {
                readElement((int)header.wrapPosition(newFirst.nextPosition()), newFirst);
            }
            // the next element was read from file and will become the next first element
            first.add(newFirst);
        } else {
            newFirst = first.getFirst();
            newFirst.updateIfMoved(previous, header);
        }

        // Commit the header.
        modCount++;
        header.setFirstPosition(newFirst.getPosition());
        header.addCount(-n);
        truncateIfNeeded();
        header.write();
    }

    /**
     * Truncate file if a lot of space is empty and no copy operations are needed.
     */
    private void truncateIfNeeded() throws IOException {
        if (header.getLastPosition() >= header.getFirstPosition() && last.nextPosition() <= getMaximumFileSize()) {
            long newLength = header.getLength();
            long goalLength = newLength / 2;
            long bytesUsed = usedBytes();
            long maxExtent = last.nextPosition();

            while (goalLength >= storage.getMinimumLength()
                    && maxExtent <= goalLength
                    && bytesUsed <= goalLength / 2) {
                newLength = goalLength;
                goalLength /= 2;
            }
            if (newLength < header.getLength()) {
                logger.debug("Truncating {} from {} to {}", this, header.getLength(), newLength);
                storage.resize(newLength);
                header.setLength(newLength);
            }
        }
    }

    /** Clears this queue. Truncates the file to the initial size. */
    public void clear() throws IOException {
        requireNotClosed();

        first.clear();
        last.reset();
        header.clear();

        if (header.getLength() != storage.getMinimumLength()) {
            storage.resize(storage.getMinimumLength());
            header.setLength(storage.getMinimumLength());
        }

        header.write();

        modCount++;
    }

    private void requireNotClosed() throws IOException {
        if (storage.isClosed()) {
            throw new IOException("closed");
        }
    }

    @Override
    public void close() throws IOException {
        storage.close();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "[storage=" + storage
                + ", header=" + header
                + ", first=" + first
                + ", last=" + last
                + "]";
    }

    private class QueueFileInputStream extends InputStream {
        private final int totalLength;
        private final int expectedModCount;
        private final byte[] singleByteArray = new byte[1];
        private long storagePosition;
        private int bytesRead;

        public QueueFileInputStream(QueueFileElement element) {
            logger.trace("QueueFileInputStream for element {}", element);
            this.storagePosition = header.wrapPosition(element.dataPosition());
            this.totalLength = element.getLength();
            this.expectedModCount = modCount;
            this.bytesRead = 0;
        }

        @Override
        public int available() {
            return totalLength - bytesRead;
        }

        @Override
        public long skip(long byteCount) {
            int countAvailable = (int)Math.min(byteCount, totalLength - bytesRead);
            bytesRead += countAvailable;
            storagePosition = header.wrapPosition(storagePosition + countAvailable);
            return countAvailable;
        }

        @Override
        public int read() throws IOException {
            if (read(singleByteArray, 0, 1) != 1) {
                throw new IOException("Cannot read byte");
            }
            return singleByteArray[0] & 0xFF;
        }

        @Override
        public int read(@NonNull byte[] bytes, int offset, int count) throws IOException {
            if (bytesRead == totalLength) {
                return -1;
            }
            if (count < 0) {
                throw new IndexOutOfBoundsException("length < 0");
            }
            if (count == 0) {
                return 0;
            }
            checkForComodification();

            int countAvailable = Math.min(count, totalLength - bytesRead);
            storagePosition = storage.read(storagePosition, bytes, offset, countAvailable);
            bytesRead += countAvailable;
            return countAvailable;
        }

        private void checkForComodification() throws IOException {
            if (modCount != expectedModCount) {
                throw new IOException("Buffer modified while reading InputStream");
            }
        }

        @Override
        public String toString() {
            return "QueueFileInputStream[length=" + totalLength + ",bytesRead=" + bytesRead + "]";
        }
    }

    public static void checkOffsetAndCount(byte[] bytes, int offset, int length) {
        if (offset < 0) {
            throw new IndexOutOfBoundsException("offset < 0");
        }
        if (length < 0) {
            throw new IndexOutOfBoundsException("length < 0");
        }
        if (offset + length > bytes.length) {
            throw new IndexOutOfBoundsException(
                    "extent of offset and length larger than buffer length");
        }
    }

    public long getMaximumFileSize() {
        return storage.getMaximumLength();
    }

    void commitOutputStream(QueueFileElement newFirst, QueueFileElement newLast, int count) throws IOException {
        if (!newLast.isEmpty()) {
            last.update(newLast);
            header.setLastPosition(newLast.getPosition());
        }
        if (!newFirst.isEmpty() && first.isEmpty()) {
            first.add(newFirst);
            header.setFirstPosition(newFirst.getPosition());
        }
        storage.flush();
        header.addCount(count);
        header.write();
        modCount++;
    }

    public void setFileLength(long size, long position, long beginningOfFirstElement) throws IOException {
        if (size > getMaximumFileSize()) {
            throw new IllegalArgumentException("File length may not exceed maximum file length");
        }
        long oldLength = header.getLength();
        if (size < oldLength) {
            throw new IllegalArgumentException("File length may not be decreased");
        }
        if (beginningOfFirstElement >= oldLength || beginningOfFirstElement < 0) {
            throw new IllegalArgumentException("First element may not exceed file size");
        }
        if (position > oldLength || position < 0) {
            throw new IllegalArgumentException("Position may not exceed file size");
        }

        storage.resize(size);
        header.setLength(size);

        // Calculate the position of the tail end of the data in the ring buffer
        // If the buffer is split, we need to make it contiguous
        if (position <= beginningOfFirstElement) {
            if (position > QueueFileHeader.HEADER_LENGTH) {
                long count = position - QueueFileHeader.HEADER_LENGTH;
                storage.move(QueueFileHeader.HEADER_LENGTH, oldLength, count);
            }
            modCount++;

            // Last position was moved forward in the copy
            long positionUpdate = oldLength - QueueFileHeader.HEADER_LENGTH;
            if (header.getLastPosition() < beginningOfFirstElement) {
                header.setLastPosition(header.getLastPosition() + positionUpdate);
                last.setPosition(header.getLastPosition());
            }
        }

        header.write();
    }

    public static long bytesToLong(byte[] b, int startIndex) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= b[i + startIndex] & 0xFF;
        }
        return result;
    }

    public static void longToBytes(long value, byte[] b, int startIndex) {
        b[startIndex] = (byte)((value >> 56) & 0xFF);
        b[startIndex + 1] = (byte)((value >> 48) & 0xFF);
        b[startIndex + 2] = (byte)((value >> 40) & 0xFF);
        b[startIndex + 3] = (byte)((value >> 32) & 0xFF);
        b[startIndex + 4] = (byte)((value >> 24) & 0xFF);
        b[startIndex + 5] = (byte)((value >> 16) & 0xFF);
        b[startIndex + 6] = (byte)((value >> 8) & 0xFF);
        b[startIndex + 7] = (byte)(value & 0xFF);
    }

    public static void intToBytes(int value, byte[] b, int startIndex) {
        b[startIndex] = (byte)((value >> 24) & 0xFF);
        b[startIndex + 1] = (byte)((value >> 16) & 0xFF);
        b[startIndex + 2] = (byte)((value >> 8) & 0xFF);
        b[startIndex + 3] = (byte)(value & 0xFF);
    }

    public static int bytesToInt(byte[] b, int startIndex) {
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result <<= 8;
            result |= b[i + startIndex] & 0xFF;
        }
        return result;
    }
}
