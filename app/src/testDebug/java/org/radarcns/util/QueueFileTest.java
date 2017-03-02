package org.radarcns.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
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
            exception.expect(IndexOutOfBoundsException.class);
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
        queueFile.remove();
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
        queueFile.remove();
        try (InputStream in = queueFile.peek()) {
            assertNotNull(in);
            assertEquals(1, in.available());
            assertEquals(v2, in.read());
        }
        queueFile.remove();
        try (InputStream in = queueFile.peek()) {
            assertNotNull(in);
            assertEquals(16, in.available());
            byte[] actualBuffer = new byte[20];
            assertEquals(16, in.read(actualBuffer));
            byte[] actualBufferShortened = new byte[16];
            System.arraycopy(actualBuffer, 0, actualBufferShortened, 0, 16);
            assertArrayEquals(buffer, actualBufferShortened);
        }
        queueFile.remove();
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

    private void writeAssertFileSize(int expectedSize, byte[] buffer, QueueFile queue) throws IOException {
        try (QueueFile.QueueFileOutputStream out = queue.elementOutputStream()) {
            out.write(buffer);
        }
        assertEquals(expectedSize, queue.fileSize());
    }

    @Test
    public void fileSize() throws Exception {
        QueueFile queue = createQueue();
        assertEquals(QueueFile.MINIMUM_SIZE, queue.fileSize());
        byte[] buffer = new byte[MAX_SIZE / 16 - 40];
        // write buffer, assert that the file size increases with the stored size
        writeAssertFileSize(QueueFile.MINIMUM_SIZE, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 2, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 2, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 4, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 4, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 4, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 4, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, buffer, queue);
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, buffer, queue);

        // queue is full now
        Exception actualException = null;
        try {
            writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, buffer, queue);
        } catch (IOException ex) {
            actualException = ex;
        }
        assertNotNull(actualException);
        // queue is full, remove elements to add new ones
        queue.remove();
        // this buffer is written in a circular way
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, buffer, queue);
        queue.remove();
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, buffer, queue);
        queue.remove();
        writeAssertFileSize(QueueFile.MINIMUM_SIZE * 8, buffer, queue);
        queue.remove(14);
        assertEquals(2, queue.size());
        assertEquals(QueueFile.MINIMUM_SIZE * 2, queue.fileSize());
    }
}