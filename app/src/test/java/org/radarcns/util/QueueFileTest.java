package org.radarcns.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static org.junit.Assert.*;

public class QueueFileTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void peek() throws Exception {
        File testFile = folder.newFile();
        RandomAccessFile raf = new RandomAccessFile(testFile, "rwd");
        raf.setLength(100L);
        FileChannel channel = raf.getChannel();
        channel.position(0);
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putInt(1);
        buffer.putInt(2);
        buffer.putInt(3);
        buffer.flip();
        int totalWritten = 0;
        while (buffer.hasRemaining()) {
            totalWritten += channel.write(buffer);
        }
        assertEquals(12, totalWritten);

        buffer.flip();
        assertEquals(1, buffer.getInt());
        assertEquals(2, buffer.getInt());
        assertEquals(3, buffer.getInt());

        channel.position(0);

        ByteBuffer newBuffer = ByteBuffer.allocate(20);
        newBuffer.position(8);
        newBuffer.flip();
        channel.read(newBuffer);
        newBuffer.flip();

        assertEquals(1, newBuffer.getInt());
        assertEquals(2, newBuffer.getInt());
        exception.expect(BufferUnderflowException.class);
        assertEquals(3, newBuffer.getInt());
    }
}