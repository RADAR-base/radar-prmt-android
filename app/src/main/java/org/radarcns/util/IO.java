package org.radarcns.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class IO {
    /**
     * Read all contents of a bounded input stream. Warning: this will not exit on an unbounded
     * stream. It uses the UTF-8 to interpret the stream.
     * @param in: bounded InputStream
     * @return the contents of the stream.
     * @throws IllegalArgumentException if the stream is null
     */
    public static String readInputStream(InputStream in) throws IOException {
        if (in == null) {
            throw new IOException("Input stream cannot be found");
        }
        try (Scanner s = new Scanner(in, "UTF-8")) {
            s.useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        }
    }
}
