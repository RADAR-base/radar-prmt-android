package org.radarcns.util;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * Storage for a queue. Data in the queue must be written contiguously starting at position 0. The
 * storage uses wraparound, so data written that would exceed the end of the storage, will be
 * written to the beginning.
 */

public interface QueueStorage extends Closeable, Flushable {
    /**
     * Write data to storage medium. The position will wrap around.
     * @param position position to write to
     * @param buffer buffer to write
     * @param offset offset in buffer to write
     * @param count number of bytes to write
     * @throws IndexOutOfBoundsException if {@code position < 0},
     *                                   {@code offset < 0}, {@code count < 0},
     *                                   {@code offset + count > buffer.length}, or
     *                                   {@code count > file size - QueueFileHeader.HEADER_LENGTH}
     * @throws IOException if the storage is full or cannot be written to
     * @return wrapped position after the write)
     */
    long write(long position, byte[] buffer, int offset, int count) throws IOException;

    /**
     * Read data from storage medium. The position will wrap around.
     * @param position position read from
     * @param buffer buffer to read data into
     * @param offset offset in buffer read data to
     * @param count number of bytes to read
     * @throws IndexOutOfBoundsException if {@code position < QueueFileHeader.HEADER_LENGTH},
     *                                   {@code offset < 0}, {@code count < 0}, or
     *                                   {@code offset + count > buffer.length}
     * @throws IOException if the storage cannot be read.
     * @return wrapped position after the read
     */
    long read(long position, byte[] buffer, int offset, int count) throws IOException;

    /**
     * Move part of the storage to another location, overwriting any data on the previous location.
     *
     * @throws IllegalArgumentException if {@code srcPosition < QueueFileHeader.HEADER_LENGTH},
     *                                  {@code dstPosition < QueueFileHeader.HEADER_LENGTH},
     *                                  {@code count <= 0}, {@code srcPosition + count > size}
     *                                  or {@code dstPosition + count > size}
     */
    void move(long srcPosition, long dstPosition, long count) throws IOException;

    /**
     * Resize the storage. If the size is made smaller, after the given size is discarded. If the
     * size is made larger, the new part is not yet used. To use a new part of the storage, write to
     * it contiguously from previously written data.
     *
     * @param size new size in bytes.
     * @throws IllegalArgumentException if {@code size < QueueFileHeader.HEADER_LENGTH} or
     *                                  {@code size > #getMaximumSize()}.
     * @throws IOException if the storage could not be resized
     */
    void resize(long size) throws IOException;

    /**
     * Current size of the storage.
     * @return size in bytes
     */
    long length();

    /** Minimum size of the storage in bytes. */
    long getMinimumLength();

    /** Maximum size of the storage in bytes. */
    long getMaximumLength();

    /** Whether the close function was called. */
    boolean isClosed();

    /** Whether underlying file existed when the current object was created. */
    boolean existed();
}
