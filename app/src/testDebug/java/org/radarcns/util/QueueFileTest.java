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
    private static final int MAX_SIZE = 8 * QueueFile.MINIMUM_SIZE;
    private static final Logger logger = LoggerFactory.getLogger(QueueFileTest.class);
    private static final int ELEMENT_HEADER_LENGTH = 5;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void elementOutputStream() throws Exception {
        QueueFile queue = createQueue();
        byte[] buffer = new byte[MAX_SIZE / 4];
        try (QueueFile.QueueFileOutputStream out = queue.elementOutputStream()) {
            out.write(buffer);
            out.nextElement();
            out.write(buffer);
            out.nextElement();
            out.write(buffer);
            out.nextElement();
            exception.expect(IOException.class);
            out.write(buffer);
        }
    }

    @Test
    public void elementOutputStreamCircular() throws Exception {
        QueueFile queue = createQueue();
        byte[] buffer = new byte[MAX_SIZE / 4];
        try (QueueFile.QueueFileOutputStream out = queue.elementOutputStream()) {
            out.write(buffer);
            out.nextElement();
            out.write(buffer);
            out.nextElement();
            out.write(buffer);
            out.nextElement();
        }
        queue.remove(2);
        try (QueueFile.QueueFileOutputStream out = queue.elementOutputStream()) {
            out.write(buffer);
            out.nextElement();
            out.write(buffer);
            out.nextElement();
            exception.expect(IOException.class);
            out.write(buffer);
        }
    }

    private QueueFile createQueue() throws IOException {
        File file = folder.newFile();
        assertTrue(file.delete());
        return new QueueFile(file, MAX_SIZE);
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
        try (QueueFile.QueueFileOutputStream out = queueFile.elementOutputStream()) {
            out.write(v1);
            out.nextElement();
            out.write(v2);
            out.nextElement();
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
        try (QueueFile.QueueFileOutputStream out = queueFile.elementOutputStream()) {
            out.write(1);
            out.nextElement();
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
        try (QueueFile.QueueFileOutputStream out = queue.elementOutputStream()) {
            out.write(1);
            out.nextElement();
            out.write(2);
        }
        assertEquals(2, queue.size());
        queue.clear();
        assertTrue(queue.isEmpty());
    }

    private void writeAssertFileSize(int expectedSize, int expectedUsed, byte[] buffer, QueueFile queue) throws IOException {
        try (QueueFile.QueueFileOutputStream out = queue.elementOutputStream()) {
            out.write(buffer);
        }
        assertEquals(expectedUsed, queue.usedBytes());
        assertEquals(expectedSize, queue.fileSize());
    }

    @Test
    public void fileSize() throws Exception {
        QueueFile queue = createQueue();
        assertEquals(QueueFile.MINIMUM_SIZE, queue.fileSize());
        int bufSize = MAX_SIZE / 16 - QueueFile.HEADER_LENGTH;
        byte[] buffer = new byte[bufSize];
        // write buffer, assert that the file size increases with the stored size
        writeAssertFileSize(QueueFile.MINIMUM_SIZE, (bufSize + ELEMENT_HEADER_LENGTH) + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE, (bufSize + ELEMENT_HEADER_LENGTH)*2 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 2, (bufSize + ELEMENT_HEADER_LENGTH)*3 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 2, (bufSize + ELEMENT_HEADER_LENGTH)*4 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 4, (bufSize + ELEMENT_HEADER_LENGTH)*5 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 4, (bufSize + ELEMENT_HEADER_LENGTH)*6 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 4, (bufSize + ELEMENT_HEADER_LENGTH)*7 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 4, (bufSize + ELEMENT_HEADER_LENGTH)*8 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, (bufSize + ELEMENT_HEADER_LENGTH)*9 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, (bufSize + ELEMENT_HEADER_LENGTH)*10 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, (bufSize + ELEMENT_HEADER_LENGTH)*11 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, (bufSize + ELEMENT_HEADER_LENGTH)*12 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, (bufSize + ELEMENT_HEADER_LENGTH)*13 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, (bufSize + ELEMENT_HEADER_LENGTH)*14 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, (bufSize + ELEMENT_HEADER_LENGTH)*15 + QueueFile.HEADER_LENGTH, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, (bufSize + ELEMENT_HEADER_LENGTH)*16 + QueueFile.HEADER_LENGTH, buffer, queue);

        // queue is full now
        Exception actualException = null;
        try {
            writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, (bufSize + ELEMENT_HEADER_LENGTH)*17 + QueueFile.HEADER_LENGTH, buffer, queue);
        } catch (IOException ex) {
            actualException = ex;
        }
        assertNotNull(actualException);
        // queue is full, remove elements to add new ones
        queue.remove(1);
        // this buffer is written in a circular way
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, (bufSize + ELEMENT_HEADER_LENGTH)*16 + QueueFile.HEADER_LENGTH, buffer, queue);
        queue.remove(1);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, (bufSize + ELEMENT_HEADER_LENGTH)*16 + QueueFile.HEADER_LENGTH, buffer, queue);
        queue.remove(1);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, (bufSize + ELEMENT_HEADER_LENGTH)*16 + QueueFile.HEADER_LENGTH, buffer, queue);
        queue.remove(14);
        assertEquals(2, queue.size());
        assertEquals((bufSize + ELEMENT_HEADER_LENGTH)*2 + QueueFile.HEADER_LENGTH, queue.usedBytes());
        assertEquals(QueueFile.MINIMUM_SIZE * 2, queue.fileSize());
    }

    @Test
    public void enduranceTest() throws Throwable {
        int size = QueueFile.MINIMUM_SIZE*2;
        Random random = new Random();
        byte[] buffer = new byte[size / 16];
        File file = folder.newFile();
        assertTrue(file.delete());
        QueueFile queue = new QueueFile(file, size);
        LinkedList<Element> list = new LinkedList<>();
        int bytesUsed = 36;

        try {
            for (int i = 0; i < 1000; i++) {
                double choice = random.nextDouble();
                if (choice < 0.25 && !queue.isEmpty()) {
                    bytesUsed -= remove(list, queue, random);
                } else if (choice < 0.5 && !queue.isEmpty()) {
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
            removedBytes += list.removeFirst().length + ELEMENT_HEADER_LENGTH;
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
        try (QueueFile.QueueFileOutputStream out = queue.elementOutputStream()) {
            for (int j = 0; j < numAdd; j++) {
                int numBytes = random.nextInt(buffer.length) + 1;
                if ((long) numBytes + out.bytesNeeded() + ELEMENT_HEADER_LENGTH > size) {
                    logger.info("Not adding to full queue");
                    break;
                }
                Element next = new Element(0, numBytes);
                if (list.isEmpty()) {
                    next.position = QueueFile.HEADER_LENGTH;
                } else if (out.bytesNeeded() + numBytes + ELEMENT_HEADER_LENGTH > queue.fileSize()) {
                    int firstPosition = list.getFirst().position;
                    for (Element el : list) {
                        if (el.position < firstPosition) {
                            el.position += queue.fileSize() - QueueFile.HEADER_LENGTH;
                        }
                    }
                    Element last = list.getLast();
                    next.position = last.position + last.length + ELEMENT_HEADER_LENGTH;
                    if (next.position >= queue.fileSize() * 2) {
                        next.position += QueueFile.HEADER_LENGTH - queue.fileSize() * 2;
                    }
                } else {
                    Element last = list.getLast();
                    next.position = last.position + last.length + ELEMENT_HEADER_LENGTH;
                    if (next.position >= queue.fileSize()) {
                        next.position += QueueFile.HEADER_LENGTH - queue.fileSize();
                    }
                }
                bytesUsed += next.length + ELEMENT_HEADER_LENGTH;
                list.add(next);
                out.write(buffer, 0, numBytes);
                out.nextElement();
            }
        }
        return bytesUsed;
    }

    private static class Element {
        public int position;
        public int length;
        private Element(int position, int length) {
            this.position = position;
            this.length = length;
        }
        public String toString() {
            return "[" + position + ", " + length + "]";
        }
    }
}