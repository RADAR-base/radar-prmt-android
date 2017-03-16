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

import java.io.IOException;

/**
 * Header for a {@link QueueFile}.
 *
 * This class is an adaptation of com.squareup.tape2, allowing multi-element writes. It also
 * removes legacy support.
 *
 */
public class QueueFileHeader {
    /** The header length in bytes. */
    public static final int HEADER_LENGTH = 36;

    /** Leading bit set to 1 indicating a versioned header and the version of 1. */
    private static final int VERSIONED_HEADER = 0x00000001;

    /** Buffer to read and store the header with. */
    private final byte[] headerBuffer = new byte[HEADER_LENGTH];

    /** Storage to read and write the header. */
    private final QueueStorage storage;

    /** Cached file length. Always a power of 2. */
    private long length;

    /** Number of elements. */
    private int count;

    /** Version number. Currently fixed to {@link #VERSIONED_HEADER}. */
    private final int version;

    /** Position of the first (front-most) element in the queue. */
    private long firstPosition;

    /** Position of the last (back-most) element in the queue. */
    private long lastPosition;

    /**
     * QueueFileHeader that matches storage. If the storage already existed, the header is read from
     * the file. Otherwise, the header is initialized and written to file.
     * @param storage medium to write to.
     * @throws IOException if the storage cannot be read or contains invalid data.
     */
    public QueueFileHeader(QueueStorage storage) throws IOException {
        this.storage = storage;
        version = VERSIONED_HEADER;
        if (this.storage.existed()) {
            read();
        } else {
            length = this.storage.length();
            if (length < HEADER_LENGTH) {
                throw new IOException("Storage does not contain header.");
            }
            count = 0;
            firstPosition = 0L;
            lastPosition = 0L;
            write();
        }
    }

    /** To initialize the header, read it from file. */
    private void read() throws IOException {
        storage.read(0L, headerBuffer, 0, HEADER_LENGTH);

        int version = QueueFile.bytesToInt(headerBuffer, 0);
        if (version != VERSIONED_HEADER) {
            throw new IOException("Storage " + storage + " is not recognized as a queue file.");
        }
        length = QueueFile.bytesToLong(headerBuffer, 4);
        if (length > storage.length()) {
            throw new IOException("File is truncated. Expected length: " + length
                    + ", Actual length: " + storage.length());
        }
        count = QueueFile.bytesToInt(headerBuffer, 12);
        firstPosition = QueueFile.bytesToLong(headerBuffer, 16);
        lastPosition = QueueFile.bytesToLong(headerBuffer, 24);

        if (length < HEADER_LENGTH) {
            throw new IOException("File length in " + storage + " header too small");
        }
        if (firstPosition < 0 || firstPosition > length
                || lastPosition < 0 || lastPosition > length) {
            throw new IOException("Element offsets point outside of storage " + storage);
        }
        if (count < 0 || (count > 0 && (firstPosition == 0 || lastPosition == 0))) {
            throw new IOException("Number of elements not correct in storage " + storage);
        }
        int crc = QueueFile.bytesToInt(headerBuffer, 32);
        if (crc != hashCode()) {
            throw new IOException("Queue storage " + storage + " was corrupted.");
        }
    }

    /**
     * Writes the header to file in a single write operation. This will flush the storage.
     * @throws IOException if the header could not be written
     */
    public void write() throws IOException {
        // first write all variables to a single byte buffer
        QueueFile.intToBytes(VERSIONED_HEADER, headerBuffer, 0);
        QueueFile.longToBytes(length, headerBuffer, 4);
        QueueFile.intToBytes(count, headerBuffer, 12);
        QueueFile.longToBytes(firstPosition, headerBuffer, 16);
        QueueFile.longToBytes(lastPosition, headerBuffer, 24);
        QueueFile.intToBytes(hashCode(), headerBuffer, 32);

        // then write the byte buffer out in one go
        storage.write(0L, headerBuffer, 0, HEADER_LENGTH);
        storage.flush();
    }

    /** Get the stored length of the QueueStorage in bytes. */
    public long getLength() {
        return length;
    }

    /**
     * Set the stored length of the QueueStorage in bytes. This does not modify the storage length
     * itself.
     */
    public void setLength(long length) {
        this.length = length;
    }

    /** Get the number of elements in the QueueFile. */
    public int getCount() {
        return count;
    }

    /** Add given number of elements to the current number of elements in the QueueFile. */
    public void addCount(int count) {
        this.count += count;
    }

    /** Get the position of the first element in the QueueFile. */
    public long getFirstPosition() {
        return firstPosition;
    }

    /** Set the position of the first element in the QueueFile. */
    public void setFirstPosition(long firstPosition) {
        this.firstPosition = firstPosition;
    }

    /** Get the position of the last element in the QueueFile. */
    public long getLastPosition() {
        return lastPosition;
    }

    /** Set the position of the last element in the QueueFile. */
    public void setLastPosition(long lastPosition) {
        this.lastPosition = lastPosition;
    }

    /**
     * Hash function for the header, so that it can be verified.
     */
    @Override
    public int hashCode() {
        int result = version;
        result = 31 * result + (int)((length >> 32) ^ length);
        result = 31 * result + count;
        result = 31 * result + (int)((firstPosition >> 32) ^ firstPosition);
        result = 31 * result + (int)((lastPosition >> 32) ^ lastPosition);
        return result;
    }

    /** Wraps the position if it exceeds the end of the file. */
    public long wrapPosition(long position) {
        long newPosition = position < length ? position : HEADER_LENGTH + position - length;
        if (newPosition >= length || newPosition < 0) {
            throw new IllegalArgumentException("Position " + position + " invalid outside of storage length " + length);
        }
        return newPosition;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "[length=" + length
                + ", size=" + count
                + ", first=" + firstPosition
                + ", last=" + lastPosition
                + "]";
    }

    /** Clear the positions and count. This does not change the stored file length. */
    public void clear() {
        count = 0;
        firstPosition = 0L;
        lastPosition = 0L;
    }
}
