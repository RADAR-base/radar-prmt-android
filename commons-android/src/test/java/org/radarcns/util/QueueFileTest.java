package org.radarcns.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QueueFileTest {
    private static final int MAX_SIZE = 8 * MappedQueueFileStorage.MINIMUM_LENGTH;
    private static final Logger logger = LoggerFactory.getLogger(QueueFileTest.class);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void elementOutputStream() throws Exception {
        QueueFile queue = createQueue();
        byte[] buffer = new byte[MAX_SIZE / 4];
        try (QueueFileOutputStream out = queue.elementOutputStream()) {
            out.write(buffer);
            out.next();
            out.write(buffer);
            out.next();
            out.write(buffer);
            out.next();
            exception.expect(IOException.class);
            out.write(buffer);
        }
    }

    @Test
    public void elementOutputStreamCircular() throws Exception {
        QueueFile queue = createQueue();
        byte[] buffer = new byte[MAX_SIZE / 4];
        try (QueueFileOutputStream out = queue.elementOutputStream()) {
            out.write(buffer);
            out.next();
            out.write(buffer);
            out.next();
            out.write(buffer);
            out.next();
        }
        queue.remove(2);
        try (QueueFileOutputStream out = queue.elementOutputStream()) {
            out.write(buffer);
            out.next();
            out.write(buffer);
            out.next();
            exception.expect(IOException.class);
            out.write(buffer);
        } catch (IOException ex) {
            logger.info("Queue file cannot be written to {}", queue);
            throw ex;
        }
    }

    private QueueFile createQueue() throws IOException {
        File file = folder.newFile();
        assertTrue(file.delete());
        return QueueFile.newMapped(file, MAX_SIZE);
    }

    @Test
    public void isEmpty() throws Exception {
        QueueFile queueFile = createQueue();
        assertTrue(queueFile.isEmpty());
        int value = 1;
        try (OutputStream out = queueFile.elementOutputStream()) {
            out.write(value);
        }
        assertFalse(queueFile.isEmpty());
        assertEquals(1, queueFile.size());
        InputStream in = queueFile.peek();
        assertNotNull(in);
        in.close();
        assertFalse(queueFile.isEmpty());
        assertEquals(1, queueFile.size());
        queueFile.remove(1);
        assertTrue(queueFile.isEmpty());
        assertEquals(0, queueFile.size());
    }

    @Test
    public void peek() throws Exception {
        QueueFile queueFile = createQueue();
        assertNull(queueFile.peek());
        Random random = new Random();
        byte[] buffer = new byte[16];
        int v1 = random.nextInt(255);
        int v2 = random.nextInt(255);
        random.nextBytes(buffer);
        byte[] expectedBuffer = new byte[buffer.length];
        System.arraycopy(buffer, 0, expectedBuffer, 0, buffer.length);
        try (QueueFileOutputStream out = queueFile.elementOutputStream()) {
            out.write(v1);
            out.next();
            out.write(v2);
            out.next();
            out.write(buffer);
        }
        assertEquals(3, queueFile.size());
        try (InputStream in = queueFile.peek()) {
            assertNotNull(in);
            assertEquals(1, in.available());
            assertEquals(v1, in.read());
        }
        try (InputStream in = queueFile.peek()) {
            assertNotNull(in);
            assertEquals(1, in.available());
            assertEquals(v1, in.read());
        }
        queueFile.remove(1);
        try (InputStream in = queueFile.peek()) {
            assertNotNull(in);
            assertEquals(1, in.available());
            assertEquals(v2, in.read());
        }
        queueFile.remove(1);
        try (InputStream in = queueFile.peek()) {
            assertNotNull(in);
            assertEquals(16, in.available());
            byte[] actualBuffer = new byte[20];
            assertEquals(16, in.read(actualBuffer));
            byte[] actualBufferShortened = new byte[16];
            System.arraycopy(actualBuffer, 0, actualBufferShortened, 0, 16);
            assertArrayEquals(expectedBuffer, actualBufferShortened);
        }
        queueFile.remove(1);
        try (InputStream in = queueFile.peek()) {
            assertNull(in);
        }
    }

    @Test
    public void iterator() throws Exception {
        QueueFile queueFile = createQueue();
        assertNull(queueFile.peek());
        try (QueueFileOutputStream out = queueFile.elementOutputStream()) {
            out.write(1);
            out.next();
            out.write(2);
        }
        Iterator<InputStream> iter = queueFile.iterator();
        assertTrue(iter.hasNext());
        try (InputStream in = iter.next()) {
            assertEquals(1, in.read());
        }
        assertTrue(iter.hasNext());
        try (InputStream in = iter.next()) {
            assertEquals(2, in.read());
        }
        assertFalse(iter.hasNext());

        exception.expect(NoSuchElementException.class);
        iter.next();
    }

    @Test
    public void clear() throws Exception {
        QueueFile queue = createQueue();
        try (QueueFileOutputStream out = queue.elementOutputStream()) {
            out.write(1);
            out.next();
            out.write(2);
        }
        assertEquals(2, queue.size());
        queue.clear();
        assertTrue(queue.isEmpty());
    }

    private void writeAssertFileSize(int expectedSize, int expectedUsed, byte[] buffer, QueueFile queue) throws IOException {
        try (QueueFileOutputStream out = queue.elementOutputStream()) {
            out.write(buffer);
        }
        assertEquals(expectedUsed, queue.usedBytes());
        assertEquals(expectedSize, queue.fileSize());
    }

    @Test
    public void fileSize() throws Exception {
        QueueFile queue = createQueue();
        assertEquals(MappedQueueFileStorage.MINIMUM_LENGTH, queue.fileSize());
        int bufSize = MAX_SIZE / 16 - QueueFileHeader.HEADER_LENGTH;
        byte[] buffer = new byte[bufSize];
        // write buffer, assert that the file size increases with the stored size
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH, (bufSize + QueueFileElement.HEADER_LENGTH) + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH, (bufSize + QueueFileElement.HEADER_LENGTH)*2 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 2, (bufSize + QueueFileElement.HEADER_LENGTH)*3 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 2, (bufSize + QueueFileElement.HEADER_LENGTH)*4 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 4, (bufSize + QueueFileElement.HEADER_LENGTH)*5 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 4, (bufSize + QueueFileElement.HEADER_LENGTH)*6 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 4, (bufSize + QueueFileElement.HEADER_LENGTH)*7 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 4, (bufSize + QueueFileElement.HEADER_LENGTH)*8 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.HEADER_LENGTH)*9 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.HEADER_LENGTH)*10 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.HEADER_LENGTH)*11 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.HEADER_LENGTH)*12 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.HEADER_LENGTH)*13 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.HEADER_LENGTH)*14 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.HEADER_LENGTH)*15 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.HEADER_LENGTH)*16 + QueueFileHeader.HEADER_LENGTH, buffer, queue);

        // queue is full now
        Exception actualException = null;
        try {
            writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.HEADER_LENGTH)*17 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        } catch (IOException ex) {
            actualException = ex;
        }
        assertNotNull(actualException);
        // queue is full, remove elements to add new ones
        queue.remove(1);
        // this buffer is written in a circular way
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.HEADER_LENGTH)*16 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        queue.remove(1);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.HEADER_LENGTH)*16 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        queue.remove(1);
        writeAssertFileSize(MappedQueueFileStorage.MINIMUM_LENGTH * 8, (bufSize + QueueFileElement.HEADER_LENGTH)*16 + QueueFileHeader.HEADER_LENGTH, buffer, queue);
        queue.remove(14);
        assertEquals(2, queue.size());
        assertEquals((bufSize + QueueFileElement.HEADER_LENGTH)*2 + QueueFileHeader.HEADER_LENGTH, queue.usedBytes());
        assertEquals(MappedQueueFileStorage.MINIMUM_LENGTH * 2, queue.fileSize());
    }

    @Test(timeout = 2000L)
    public void enduranceTest() throws Throwable {
        int numberOfOperations = 1000;
        int size = MappedQueueFileStorage.MINIMUM_LENGTH *2;
        Random random = new Random();
        byte[] buffer = new byte[size / 16];
        File file = folder.newFile();
        assertTrue(file.delete());
        QueueFile queue = QueueFile.newMapped(file, size);
        LinkedList<Element> list = new LinkedList<>();
        int bytesUsed = 36;

        try {
            for (int i = 0; i < numberOfOperations; i++) {
                double choice = random.nextDouble();
                if (choice < 0.05) {
                    logger.info("Closing and reopening queue");
                    queue.close();
                    queue = QueueFile.newMapped(file, size);
                } else if (choice < 0.1) {
                    logger.info("Clearing queue");
                    queue.clear();
                    list.clear();
                    bytesUsed = 36;
                } else if (choice < 0.325 && !queue.isEmpty()) {
                    bytesUsed -= remove(list, queue, random);
                } else if (choice < 0.55 && !queue.isEmpty()) {
                    read(list, queue, buffer, random);
                } else {
                    bytesUsed += write(list, queue, buffer, random, size);
                }
                assertEquals(bytesUsed, queue.usedBytes());
                assertEquals(list.size(), queue.size());
            }
        } catch (Throwable ex) {
            logger.error("Current list: {} with used bytes {}; QueueFile {}", list, bytesUsed, queue);
            throw ex;
        }
    }

    /**
     * Remove a random number of elements from a queue and a verification list
     * @return bytes removed
     */
    private int remove(LinkedList<Element> list, QueueFile queue, Random random) throws IOException {
        int numRemove = random.nextInt(queue.size()) + 1;
        logger.info("Removing {} elements", numRemove);
        queue.remove(numRemove);
        int removedBytes = 0;
        for (int j = 0; j < numRemove; j++) {
            removedBytes += list.removeFirst().length + QueueFileElement.HEADER_LENGTH;
        }
        return removedBytes;
    }

    /**
     * Read a random number of elements from a queue and a verification list, using given buffer.
     * The sizes read must match the verification list.
     */
    private void read(LinkedList<Element> list, QueueFile queue, byte[] buffer, Random random) throws Throwable {
        int numRead = random.nextInt(queue.size()) + 1;
        assertTrue(queue.size() >= numRead);
        logger.info("Reading {} elements", numRead);
        Iterator<InputStream> iterator = queue.iterator();
        for (int j = 0; j < numRead; j++) {
            Element expectedElement = list.get(j);
            InputStream in = iterator.next();
            try {
                int readLength = 0;
                int newlyRead = in.read(buffer, 0, buffer.length);
                while (newlyRead != -1) {
                    readLength += newlyRead;
                    newlyRead = in.read(buffer, readLength, buffer.length - readLength);
                }
                assertEquals(expectedElement.length, readLength);
            } catch (Throwable ex) {
                logger.error("Inputstream {} of queuefile {} does not match element {}",
                        in, queue, expectedElement);
                throw ex;
            } finally {
                in.close();
            }
        }
    }

    private int write(LinkedList<Element> list, QueueFile queue, byte[] buffer, Random random, int size) throws IOException {
        int numAdd = random.nextInt(16) + 1;
        logger.info("Adding {} elements", numAdd);
        int bytesUsed = 0;
        try (QueueFileOutputStream out = queue.elementOutputStream()) {
            for (int j = 0; j < numAdd; j++) {
                int numBytes = random.nextInt(buffer.length) + 1;
                if ((long) numBytes + out.usedSize() + QueueFileElement.HEADER_LENGTH > size) {
                    logger.info("Not adding to full queue");
                    break;
                }
                Element next = new Element(0, numBytes);
                if (list.isEmpty()) {
                    next.position = QueueFileHeader.HEADER_LENGTH;
                } else if (out.usedSize() + numBytes + QueueFileElement.HEADER_LENGTH > queue.fileSize()) {
                    int firstPosition = list.getFirst().position;
                    for (Element el : list) {
                        if (el.position < firstPosition) {
                            el.position += queue.fileSize() - QueueFileHeader.HEADER_LENGTH;
                        }
                    }
                    Element last = list.getLast();
                    next.position = last.position + last.length + QueueFileElement.HEADER_LENGTH;
                    if (next.position >= queue.fileSize() * 2) {
                        next.position += QueueFileHeader.HEADER_LENGTH - queue.fileSize() * 2;
                    }
                } else {
                    Element last = list.getLast();
                    next.position = last.position + last.length + QueueFileElement.HEADER_LENGTH;
                    if (next.position >= queue.fileSize()) {
                        next.position += QueueFileHeader.HEADER_LENGTH - queue.fileSize();
                    }
                }
                bytesUsed += next.length + QueueFileElement.HEADER_LENGTH;
                list.add(next);
                out.write(buffer, 0, numBytes);
                out.next();
            }
        }
        return bytesUsed;
    }

    private static class Element {
        private int position;
        private int length;
        private Element(int position, int length) {
            this.position = position;
            this.length = length;
        }
        public String toString() {
            return "[" + position + ", " + length + "]";
        }
    }
}