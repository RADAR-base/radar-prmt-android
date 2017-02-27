/*
 * Copyright (C) 2010 Square, Inc.
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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import static org.radarcns.util.Serialization.bytesToInt;
import static org.radarcns.util.Serialization.intToBytes;

/**
 * This class is an adaptation of com.squareup.tape2, allowing multi-element writes. It also
 * removes legacy support
 *
 * A reliable, efficient, file-based, FIFO queue. Additions and removals are O(1). All operations
 * are atomic. Writes are synchronous; data will be written to disk before an operation returns.
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
 * <p><strong>NOTE:</strong> The current implementation is built for file systems that support
 * atomic segment writes (like YAFFS). Most conventional file systems don't support this; if the
 * power goes out while writing a segment, the segment will contain garbage and the file will be
 * corrupt. We'll add journaling support so this class can be used with more file systems later.
 *
 * @author Bob Lee (bob@squareup.com)
 */
public final class QueueFile implements Closeable, Iterable<byte[]> {
    /** Leading bit set to 1 indicating a versioned header and the version of 1. */
    private static final int VERSIONED_HEADER = 0x80000001;

    /** The header length in bytes: 32. */
    private static final int HEADER_LENGTH = 32;

    /** Initial file size in bytes. */
    private static final int INITIAL_LENGTH = 4096; // one file system block

    /**
     * The underlying file. Uses a ring buffer to store entries. Designed so that a modification
     * isn't committed or visible until we write the header. The header is much smaller than a
     * segment. So long as the underlying file system supports atomic segment writes, changes to the
     * queue are atomic. Storing the file length ensures we can recover from a failed expansion
     * (i.e. if setting the file length succeeds but the process dies before the data can be copied).
     * <p>
     * This implementation supports two versions of the on-disk format.
     * <pre>
     * Format:
     *   16-32 bytes      Header
     *   ...              Data
     *
     * Header (32 bytes):
     *   1 bit            Versioned indicator [0 = legacy (see "Legacy Header"), 1 = versioned]
     *   31 bits          Version, always 1
     *   8 bytes          File length
     *   4 bytes          Element count
     *   8 bytes          Head element position
     *   8 bytes          Tail element position
     *
     * Element:
     *   4 bytes          Data length
     *   ...              Data
     * </pre>
     */
    private final FileChannel channel;
    private final RandomAccessFile randomAccessFile;

    /** Keep file around for error reporting. */
    private final File file;

    /** Cached file length. Always a power of 2. */
    private int fileLength;

    /** Number of elements. */
    private int elementCount;

    /** Pointer to first (or eldest) element. */
    private Element first;

    /** Pointer to last (or newest) element. */
    private Element last;

    private MappedByteBuffer byteBuffer;

    /**
     * The number of times this file has been structurally modified — it is incremented during
     * {@link #remove(int)} and {@link #add(byte[], int, int)}. Used by {@link ElementIterator}
     * to guard against concurrent modification.
     */
    private int modCount = 0;

    private boolean closed;

    public QueueFile(File file) throws IOException {
        this.file = file;
        closed = false;

        boolean exists = file.exists();
        randomAccessFile = new RandomAccessFile(file, "rw");
        channel = this.randomAccessFile.getChannel();
        if (exists) {
            long initialFileLength = randomAccessFile.length();
            byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, initialFileLength);
            long headerFileLength = byteBuffer.getLong();
            if (headerFileLength > Integer.MAX_VALUE) {
                throw new IOException("Queue file is larger than maximum size 2 GiB");
            }
            fileLength = (int)headerFileLength;
            if (fileLength > initialFileLength) {
                throw new IOException(
                        "File is truncated. Expected length: " + fileLength + ", Actual length: " + randomAccessFile.length());
            }

            elementCount = byteBuffer.getInt();
            long firstOffset = byteBuffer.getLong();
            long lastOffset = byteBuffer.getLong();

            if (firstOffset > fileLength || lastOffset > fileLength) {
                throw new IOException("Element offsets point outside of file length");
            }

            first = readElement((int)firstOffset);
            last = readElement((int)lastOffset);
        } else {
            fileLength = INITIAL_LENGTH;
            randomAccessFile.setLength(fileLength);
            byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileLength);
            elementCount = 0;
            writeHeader(fileLength, elementCount, 0L, 0L);
            first = Element.NULL;
            last = Element.NULL;

        }
        byteBuffer.clear();
    }

    /**
     * Writes header atomically. The arguments contain the updated values. The class member fields
     * should not have changed yet. This only updates the state in the file. It's up to the caller to
     * update the class member variables *after* this call succeeds. Assumes segment writes are
     * atomic in the underlying file system.
     */
    private void writeHeader(long fileLength, int elementCount, long firstPosition, long lastPosition)
            throws IOException {
        byteBuffer.position(0);
        byteBuffer.putInt(VERSIONED_HEADER);
        byteBuffer.putLong(fileLength);
        byteBuffer.putInt(elementCount);
        byteBuffer.putLong(firstPosition);
        byteBuffer.putLong(lastPosition);
        byteBuffer.clear();
    }

    private Element readElement(int position) throws IOException {
        if (position == 0) return Element.NULL;
        ringRead(position, buffer, 0, Element.HEADER_LENGTH);
        int length = bytesToInt(buffer, 0);
        return new Element(position, length);
    }

    /** Wraps the position if it exceeds the end of the file. */
    private int wrapPosition(int position) {
        return position < fileLength ? position
                : HEADER_LENGTH + position - fileLength;
    }

    /**
     * Writes count bytes from buffer to position in file. Automatically wraps write if position is
     * past the end of the file or if buffer overlaps it.
     *
     * @param position in file to write to
     * @param buffer to write from
     * @param count # of bytes to write
     */
    private void ringWrite(int position, byte[] buffer, int offset, int count) throws IOException {
        position = wrapPosition(position);
        byteBuffer.position((int)position);
        if (position + count <= fileLength) {
            byteBuffer.put(buffer, offset, count);
        } else {
            // The write overlaps the EOF.
            // # of bytes to write before the EOF. Guaranteed to be less than Integer.MAX_VALUE.
            int firstPart = fileLength - position;
            byteBuffer.put(buffer, offset, firstPart);
            byteBuffer.position(HEADER_LENGTH);
            byteBuffer.put(buffer, offset + firstPart, count - firstPart);
        }
        byteBuffer.clear();
    }

    /**
     * Reads count bytes into buffer from file. Wraps if necessary.
     *
     * @param position in file to read from
     * @param buffer to read into
     * @param count # of bytes to read
     */
    private void ringRead(int position, byte[] buffer, int offset, int count) throws IOException {
        position = wrapPosition(position);
        byteBuffer.position(position);
        if (position + count <= fileLength) {
            byteBuffer.get(buffer, offset, count);
        } else {
            // The read overlaps the EOF.
            // # of bytes to read before the EOF. Guaranteed to be less than Integer.MAX_VALUE.
            int firstPart = fileLength - position;
            byteBuffer.get(buffer, offset, firstPart);
            byteBuffer.position(HEADER_LENGTH);
            byteBuffer.get(buffer, offset + firstPart, count - firstPart);
        }
        byteBuffer.clear();
    }

    private void read(int max) throws IOException {
        int total = 0;
        while (total < max) {
            total += channel.read(byteBuffer);
        }
    }

    /**
     * Adds an element to the end of the queue.
     *
     * @param data to copy bytes from
     */
    public void add(byte[] data) throws IOException {
        add(data, 0, data.length);
    }

    /**
     * Adds an element to the end of the queue.
     *
     * @param data to copy bytes from
     * @param offset to start from in buffer
     * @param count number of bytes to copy
     * @throws IndexOutOfBoundsException if {@code offset < 0} or {@code count < 0}, or if {@code
     * offset + count} is bigger than the length of {@code buffer}.
     */
    public void add(byte[] data, int offset, int count) throws IOException {
        if (data == null) {
            throw new NullPointerException("data == null");
        }
        if ((offset | count) < 0 || count > data.length - offset) {
            throw new IndexOutOfBoundsException();
        }
        if (closed) throw new IOException("closed");

        expandIfNecessary(count + Element.HEADER_LENGTH);

        // Insert a new element after the current last element.
        boolean wasEmpty = isEmpty();
        long position = wasEmpty ? HEADER_LENGTH
                : wrapPosition(last.position + Element.HEADER_LENGTH + last.length);
        Element newLast = new Element(position, count);

        // Write length.
        intToBytes(count, buffer, 0);
        ringWrite(newLast.position, buffer, 0, Element.HEADER_LENGTH);

        // Write data.
        ringWrite(newLast.position + Element.HEADER_LENGTH, data, offset, count);

        // Commit the addition. If wasEmpty, first == last.
        long firstPosition = wasEmpty ? newLast.position : first.position;
        writeHeader(fileLength, elementCount + 1, firstPosition, newLast.position);
        last = newLast;
        elementCount++;
        modCount++;
        if (wasEmpty) first = last; // first element
    }

    /** Add multiple elements at the end of the queue */
    public void add(byte[][] data) throws IOException {
        Objects.requireNonNull(data, "data == null");
        if (data.length == 0) {
            return;
        }
        if (closed) {
            throw new IOException("closed");
        }

        long dataLength = 0L;
        for (byte[] el : data) {
            if (el == null) {
                throw new IllegalArgumentException("data element == null");
            }
            dataLength += el.length;
        }
        expandIfNecessary(dataLength + data.length * Element.HEADER_LENGTH);

        for (byte[] el : data) {
            add(el, 0, el.length);
        }
    }

    private long usedBytes() {
        if (elementCount == 0) {
            return HEADER_LENGTH;
        }

        if (last.position >= first.position) {
            // Contiguous queue.
            return (last.position - first.position)   // all but last entry
                    + Element.HEADER_LENGTH + last.length // last entry
                    + HEADER_LENGTH;
        } else {
            // tail < head. The queue wraps.
            return last.position                      // buffer front + header
                    + Element.HEADER_LENGTH + last.length // last entry
                    + fileLength - first.position;        // buffer end
        }
    }

    private long remainingBytes() {
        return fileLength - usedBytes();
    }

    /** Returns true if this queue contains no entries. */
    public boolean isEmpty() {
        return elementCount == 0;
    }

    /**
     * If necessary, expands the file to accommodate an additional element of the given length.
     *
     * @param elementLength length of data being added
     */
    private void expandIfNecessary(int elementLength) throws IOException {
        long remainingBytes = remainingBytes();
        if (remainingBytes >= elementLength) {
            return;
        } else if ((long)elementLength + (long)remainingBytes > Integer.MAX_VALUE) {
            throw new IllegalStateException()
        }

        // Expand.
        long newLength = fileLength;
        // Double the length until we can fit the new data.
        do {
            remainingBytes += newLength;
            newLength += newLength;
        } while (remainingBytes < elementLength);

        setLength(newLength);

        // Calculate the position of the tail end of the data in the ring buffer
        int endOfLastElement = wrapPosition(last.position + Element.HEADER_LENGTH + last.length);
        int count;
        // If the buffer is split, we need to make it contiguous
        if (endOfLastElement <= first.position) {
            channel.position(fileLength); // destination position
            count = endOfLastElement - HEADER_LENGTH;
            if (channel.transferTo(HEADER_LENGTH, count, channel) != count) {
                throw new AssertionError("Copied insufficient number of bytes!");
            }
        }

        // Commit the expansion.
        if (last.position < first.position) {
            int newLastPosition = fileLength + last.position - HEADER_LENGTH;
            writeHeader(newLength, elementCount, first.position, newLastPosition);
            last = new Element(newLastPosition, last.length);
        } else {
            writeHeader(newLength, elementCount, first.position, last.position);
        }

        fileLength = newLength;
    }

    /** Sets the length of the file. */
    private void setLength(long newLength) throws IOException {
        // Set new file length (considered metadata) and sync it to storage.
        randomAccessFile.setLength(newLength);
        channel.force(true);
    }

    /** Reads the eldest element. Returns null if the queue is empty. */
    public byte[] peek() throws IOException {
        if (closed) throw new IOException("closed");
        if (isEmpty()) return null;
        int length = first.length;
        byte[] data = new byte[length];
        ringRead(first.position + Element.HEADER_LENGTH, data, 0, length);
        return data;
    }

    /**
     * Returns an iterator over elements in this QueueFile.
     *
     * <p>The iterator disallows modifications to be made to the QueueFile during iteration. Removing
     * elements from the head of the QueueFile is permitted during iteration using
     * {@link Iterator#remove()}.
     *
     * <p>The iterator may throw an unchecked {@link RuntimeException} during {@link Iterator#next()}
     * or {@link Iterator#remove()}.
     */
    @Override public Iterator<byte[]> iterator() {
        return new ElementIterator();
    }

    private final class ElementIterator implements Iterator<byte[]> {
        /** Index of element to be returned by subsequent call to next. */
        private int nextElementIndex;

        /** Position of element to be returned by subsequent call to next. */
        private int nextElementPosition;

        /**
         * The {@link #modCount} value that the iterator believes that the backing QueueFile should
         * have. If this expectation is violated, the iterator has detected concurrent modification.
         */
        private int expectedModCount;

        private ElementIterator() {
            nextElementIndex = 0;
            nextElementPosition = first.position;
            expectedModCount = modCount;
        }

        private void checkForComodification() {
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
        }

        @Override public boolean hasNext() {
            if (closed) throw new IllegalStateException("closed");
            checkForComodification();
            return nextElementIndex != elementCount;
        }

        @Override public byte[] next() {
            if (closed) throw new IllegalStateException("closed");
            checkForComodification();
            if (isEmpty()) throw new NoSuchElementException();
            if (nextElementIndex >= elementCount) throw new NoSuchElementException();

            try {
                // Read the current element.
                Element current = readElement(nextElementPosition);
                byte[] buffer = new byte[current.length];
                nextElementPosition = wrapPosition(current.position + Element.HEADER_LENGTH);
                ringRead(nextElementPosition, buffer, 0, current.length);

                // Update the pointer to the next element.
                nextElementPosition =
                        wrapPosition(current.position + Element.HEADER_LENGTH + current.length);
                nextElementIndex++;

                // Return the read element.
                return buffer;
            } catch (IOException e) {
                throw new RuntimeException("todo: throw a proper error", e);
            }
        }

        @Override public void remove() {
            checkForComodification();

            if (isEmpty()) throw new NoSuchElementException();
            if (nextElementIndex != 1) {
                throw new UnsupportedOperationException("Removal is only permitted from the head.");
            }

            try {
                QueueFile.this.remove();
            } catch (IOException e) {
                throw new RuntimeException("todo: throw a proper error", e);
            }

            expectedModCount = modCount;
            nextElementIndex--;
        }
    }

    /** Returns the number of elements in this queue. */
    public int size() {
        return elementCount;
    }

    /**
     * Removes the eldest element.
     *
     * @throws NoSuchElementException if the queue is empty
     */
    public void remove() throws IOException {
        remove(1);
    }

    /**
     * Removes the eldest {@code n} elements.
     *
     * @throws NoSuchElementException if the queue is empty
     */
    public void remove(int n) throws IOException {
        if (n < 0) {
            throw new IllegalArgumentException("Cannot remove negative (" + n + ") number of elements.");
        }
        if (n == 0) {
            return;
        }
        if (n == elementCount) {
            clear();
            return;
        }
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        if (n > elementCount) {
            throw new IllegalArgumentException(
                    "Cannot remove more elements (" + n + ") than present in queue (" + elementCount + ").");
        }

        // Read the position and length of the new first element.
        int newFirstPosition = first.position;
        int newFirstLength = first.length;
        for (int i = 0; i < n; i++) {
            newFirstPosition = wrapPosition(newFirstPosition + Element.HEADER_LENGTH + newFirstLength);
            ringRead(newFirstPosition, buffer, 0, Element.HEADER_LENGTH);
            newFirstLength = bytesToInt(buffer, 0);
        }

        // Commit the header.
        writeHeader(fileLength, elementCount - n, newFirstPosition, last.position);
        elementCount -= n;
        modCount++;
        first = new Element(newFirstPosition, newFirstLength);
    }

    /** Clears this queue. Truncates the file to the initial size. */
    public void clear() throws IOException {
        if (closed) throw new IOException("closed");

        elementCount = 0;

        // Commit the header.
        writeHeader(INITIAL_LENGTH, 0, 0, 0);

        first = Element.NULL;
        last = Element.NULL;
        if (fileLength != INITIAL_LENGTH) {
            setLength(INITIAL_LENGTH);
            fileLength = INITIAL_LENGTH;
            byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileLength);
        }
        modCount++;
    }

    /** The underlying {@link File} backing this queue. */
    public File file() {
        return file;
    }

    @Override public void close() throws IOException {
        closed = true;
        channel.close();
    }

    @Override public String toString() {
        return getClass().getSimpleName()
                + "[length=" + fileLength
                + ", size=" + elementCount
                + ", first=" + first
                + ", last=" + last
                + "]";
    }

    /** A pointer to an element. */
    private static class Element {
        private static final Element NULL = new Element(0, 0);

        /** Length of element header in bytes. */
        private static final int HEADER_LENGTH = 4;

        /** Position in file. */
        private final int position;

        /** The length of the data. */
        private final int length;

        /**
         * Constructs a new element.
         *
         * @param position within file
         * @param length of data
         */
        private Element(int position, int length) {
            this.position = position;
            this.length = length;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName()
                    + "[position=" + position
                    + ", length=" + length
                    + "]";
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || getClass() != other.getClass()) return false;

            Element otherElement = (Element)other;
            return position == otherElement.position && length == otherElement.length;
        }

        @Override
        public int hashCode() {
            return 31 * (int) (position ^ (position >>> 32)) + length;
        }
    }
//
//    public class ByteBufferBackedInputStream extends InputStream {
//        private final int totalLength;
//        private final ByteBuffer buffer;
//        private int bytesRead;
//
//        public ByteBufferBackedInputStream(int length) {
//            this.buffer = byteBuffer.duplicate();
//            this.totalLength = length;
//            bytesRead = 0;
//        }
//
//        public int read() throws IOException {
//            if (bytesRead == totalLength) {
//                return -1;
//            }
//            // wrap position
//            if (buffer.position() == fileLength) {
//                buffer.position(HEADER_LENGTH);
//            }
//            bytesRead++;
//            return buffer.get() & 0xFF;
//        }
//
//        public int read(@NonNull byte[] bytes, int offset, int length)
//                throws IOException {
//            if (bytesRead == totalLength) {
//                return -1;
//            }
//
//            int linearPart = fileLength - buffer.position();
//            if (linearPart < length) {
//                if (linearPart > 0) {
//                    buffer.get(bytes, offset, linearPart);
//                }
//                buffer.position(HEADER_LENGTH);
//                buffer.get(bytes, offset + linearPart, length - linearPart);
//            } else {
//                buffer.get(bytes, offset, length);
//            }
//            bytesRead += length;
//            return length;
//        }
//    }
}
