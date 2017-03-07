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

import static org.radarcns.util.Serialization.bytesToInt;
import static org.radarcns.util.Serialization.bytesToLong;
import static org.radarcns.util.Serialization.intToBytes;
import static org.radarcns.util.Serialization.longToBytes;

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
    private final QueueStorage storage;

    /** Cached file length. Always a power of 2. */
    private long length;

    /** Number of elements. */
    private int count;

    private final int version;

    private long firstPosition;
    private long lastPosition;

    private final byte[] headerBuffer = new byte[HEADER_LENGTH];

    public QueueFileHeader(QueueStorage storage) throws IOException {
        this.storage = storage;
        this.version = VERSIONED_HEADER;
        this.length = 0L;
        this.count = 0;
        this.firstPosition = 0L;
        this.lastPosition = 0L;
    }

    public static QueueFileHeader read(QueueStorage storage) throws IOException {
        QueueFileHeader header = new QueueFileHeader(storage);
        header.read();
        return header;
    }

    private void read() throws IOException {
        storage.read(0L, headerBuffer, 0, HEADER_LENGTH);

        int version = bytesToInt(headerBuffer, 0);
        if (version != VERSIONED_HEADER) {
            throw new IOException("Storage " + storage + " is not recognized as a queue file.");
        }
        length = bytesToLong(headerBuffer, 4);
        if (length > storage.length()) {
            throw new IOException(
                    "File is truncated. Expected length: " + length
                            + ", Actual length: " + storage.length());
        }
        count = bytesToInt(headerBuffer, 12);
        firstPosition = bytesToLong(headerBuffer, 16);
        lastPosition = bytesToLong(headerBuffer, 24);

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
        int crc = bytesToInt(headerBuffer, 32);
        if (crc != hashCode()) {
            throw new IOException("Queue storage " + storage + " was corrupted.");
        }
    }

    public void write() throws IOException {
        // first write all variables to a single byte buffer
        intToBytes(VERSIONED_HEADER, headerBuffer, 0);
        longToBytes(length, headerBuffer, 4);
        intToBytes(count, headerBuffer, 12);
        longToBytes(firstPosition, headerBuffer, 16);
        longToBytes(lastPosition, headerBuffer, 24);
        intToBytes(hashCode(), headerBuffer, 32);

        // then write the byte buffer out in one go
        storage.write(0L, headerBuffer, 0, HEADER_LENGTH);
        storage.flush();
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public long getFirstPosition() {
        return firstPosition;
    }

    public void setFirstPosition(long firstPosition) {
        this.firstPosition = firstPosition;
    }

    public long getLastPosition() {
        return lastPosition;
    }

    public void setLastPosition(long lastPosition) {
        this.lastPosition = lastPosition;
    }

    /**
     * Hash function for the header, so that it can be verified.
     */
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

    public void clear() {
        count = 0;
        firstPosition = 0L;
        lastPosition = 0L;
    }
}
