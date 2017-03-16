package org.radarcns.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A queue-like object queue that is backed by a file storage.
 * @param <T> type of objects to store.
 */
public class BackedObjectQueue<T> implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(BackedObjectQueue.class);

    private final Converter<T> converter;
    private final QueueFile queueFile;

    /**
     * Creates a new object queue from given file.
     * @param queueFile file to write objects to
     * @param converter way to convert from and to given objects
     */
    public BackedObjectQueue(QueueFile queueFile, Converter<T> converter) {
        this.queueFile = queueFile;
        this.converter = converter;
    }

    /** Number of elements in the queue. */
    public int size() {
        return this.queueFile.size();
    }

    /**
     * Add a new element to the queue.
     * @param entry element to add
     * @throws IOException if the backing file cannot be accessed, the queue is full, or the element
     *                     cannot be converted.
     */
    public void add(T entry) throws IOException {
        try (QueueFileOutputStream out = queueFile.elementOutputStream()) {
            converter.serialize(entry, out);
        }
    }

    /**
     * Add a collection of new element to the queue.
     * @param entries elements to add
     * @throws IOException if the backing file cannot be accessed, the queue is full or the element
     *                     cannot be converted.
     */
    public void addAll(Collection<? extends T> entries) throws IOException {
        try (QueueFileOutputStream out = queueFile.elementOutputStream()) {
            for (T entry : entries) {
                converter.serialize(entry, out);
                out.next();
            }
        }
    }

    /**
     * Get the front-most object in the queue. This does not remove the element.
     * @return front-most element or null if none is available
     * @throws IOException if the element could not be read or deserialized
     */
    public T peek() throws IOException {
        try (InputStream in = queueFile.peek()) {
            return converter.deserialize(in);
        }
    }

    /**
     * Get at most {@code n} front-most objects in the queue. This does not remove the elements.
     * @param n number of elements to retrieve
     * @return list of elements, with at most {@code n} elements.
     * @throws IOException if the element could not be read or deserialized
     * @throws IllegalStateException if the element could not be read
     */
    public List<T> peek(int n) throws IOException {
        Iterator<InputStream> iter = queueFile.iterator();
        List<T> results = new ArrayList<>(n);
        for (int i = 0; i < n && iter.hasNext(); i++) {
            try (InputStream in = iter.next()) {
                results.add(converter.deserialize(in));
            }
        }
        return results;
    }

    /**
     * Remove the first element from the queue.
     * @throws IOException when the element could not be removed
     * @throws NoSuchElementException if more than the available elements are requested to be removed
     */
    public void remove() throws IOException {
        remove(1);
    }

    /**
     * Remove the first {@code n} elements from the queue.
     *
     * @throws IOException when the elements could not be removed
     * @throws NoSuchElementException if more than the available elements are requested to be removed
     */
    public void remove(int n) throws IOException {
        queueFile.remove(n);
    }

    /** Returns {@code true} if this queue contains no entries. */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Close the queue. This also closes the backing file.
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        queueFile.close();
    }

    /** Converts streams into objects. */
    public interface Converter<T> {
        /**
         * Deserialize an object from given offset of given bytes
         */
        T deserialize(InputStream in) throws IOException;
        /**
         * Serialize an object to given offset of given bytes.
         */
        void serialize(T value, OutputStream out) throws IOException;
    }
}
