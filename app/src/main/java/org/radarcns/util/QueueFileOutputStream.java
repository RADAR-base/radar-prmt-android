package org.radarcns.util;

import android.support.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import static org.radarcns.util.QueueFile.intToBytes;

/**
 * An OutputStream that can write multiple elements. After finished writing one element, call
 * {@link #next()} to start writing the next.
 *
 * <p>It is very important to {@link #close()} this OutputStream, as this is the only way that the
 * data is actually committed to file.
 */
public class QueueFileOutputStream extends OutputStream {
    private static final Logger logger = LoggerFactory.getLogger(QueueFileOutputStream.class);

    private QueueFile queue;
    private final QueueFileElement current;
    private boolean closed;
    private final QueueFileElement newLast;
    private final QueueFileElement newFirst;
    private int elementsWritten;
    private long streamBytesUsed;
    private final QueueFileHeader header;
    private final byte[] elementHeaderBuffer = new byte[QueueFileElement.HEADER_LENGTH];
    private final byte[] singleByteBuffer = new byte[1];
    private final QueueStorage storage;
    private long storagePosition;

    QueueFileOutputStream(QueueFile queue, QueueFileHeader header, QueueStorage storage, long position) throws IOException {
        this.queue = queue;
        this.header = header;
        this.storage = storage;
        this.storagePosition = header.wrapPosition(position);
        this.current = new QueueFileElement(storagePosition, 0);
        closed = false;
        newLast = new QueueFileElement();
        newFirst = new QueueFileElement();
        elementsWritten = 0;
        streamBytesUsed = 0L;
    }

    @Override
    public void write(int byteValue) throws IOException {
        singleByteBuffer[0] = (byte)(byteValue & 0xFF);
        write(singleByteBuffer, 0, 1);
    }

    @Override
    public void write(@NonNull byte[] bytes, int offset, int count) throws IOException {
        QueueFile.checkOffsetAndCount(bytes, offset, count);
        if (count == 0) {
            return;
        }
        checkConditions();

        if (current.isEmpty()) {
            expandAndUpdate(QueueFileElement.HEADER_LENGTH + (long)count);
            Arrays.fill(elementHeaderBuffer, (byte)0);
            storagePosition = storage.write(storagePosition, elementHeaderBuffer, 0, QueueFileElement.HEADER_LENGTH);
        } else {
            expandAndUpdate(count);
        }

        storagePosition = storage.write(storagePosition, bytes, offset, count);
        current.setLength(current.getLength() + count);
    }

    private void checkConditions() throws IOException {
        if (closed || storage.isClosed()) {
            throw new IOException("closed");
        }
    }

    /**
     * Proceed writing the next element. Zero length elements are not written, so always write
     * at least one byte to store an element.
     * @throws IOException if the QueueFileStorage cannot be written to
     */
    public void next() throws IOException {
        checkConditions();
        if (current.isEmpty()) {
            return;
        }
        newLast.update(current);
        if (newFirst.isEmpty() && queue.isEmpty()) {
            newFirst.update(current);
        }

        current.setPosition(storagePosition);
        current.setLength(0);

        intToBytes(newLast.getLength(), elementHeaderBuffer, 0);
        elementHeaderBuffer[4] = newLast.crc();
        storage.write(newLast.getPosition(), elementHeaderBuffer, 0, QueueFileElement.HEADER_LENGTH);

        elementsWritten++;
    }

    /**
     * Size of the storage that will be used if the OutputStream is closed.
     * @return number of bytes used
     */
    public long usedSize() {
        return queue.usedBytes() + streamBytesUsed;
    }

    /** Expands the storage if necessary, updating the queue length if needed. */
    private void expandAndUpdate(long length) throws IOException {
        streamBytesUsed += length;

        long oldLength = header.getLength();

        long bytesNeeded = usedSize();
        if (bytesNeeded <= oldLength) {
            return;
        }
        if (bytesNeeded > queue.getMaximumFileSize()) {
            throw new IOException("Data does not fit in queue");
        }

        logger.debug("Extending {}", queue);

        // Double the length until we can fit the new data.
        long newLength = oldLength * 2;
        while (newLength < bytesNeeded) {
            newLength += newLength;
        }

        long beginningOfFirstElement;
        if (!newFirst.isEmpty()) {
            beginningOfFirstElement = newFirst.getPosition();
        } else {
            beginningOfFirstElement = header.getFirstPosition();
        }

        queue.setFileLength(Math.min(queue.getMaximumFileSize(), newLength), storagePosition, beginningOfFirstElement);

        if (storagePosition <= beginningOfFirstElement) {
            long positionUpdate = oldLength - QueueFileHeader.HEADER_LENGTH;

            if (current.getPosition() <= beginningOfFirstElement) {
                current.setPosition(current.getPosition() + positionUpdate);
            }
            storagePosition += positionUpdate;
        }
    }

    /**
     * Closes the stream and commits it to file.
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        try {
            next();
            if (elementsWritten > 0) {
                queue.commitOutputStream(newFirst, newLast, elementsWritten);
            }
        } finally {
            closed = true;
        }
    }

    @Override
    public String toString() {
        return "QueueFileOutputStream[current=" + current
                + ",total=" + streamBytesUsed + ",used=" + usedSize() + "]";
    }
}
