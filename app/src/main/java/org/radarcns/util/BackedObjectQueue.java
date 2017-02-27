//package org.radarcns.util;
//
//import java.io.Closeable;
//import java.io.IOException;
//import java.util.Iterator;
//
///**
// * Created by joris on 22/02/2017.
// */
//
//public class BackedObjectQueue<T> implements Iterable<T>, Closeable {
//    private final Converter<T> converter;
//    private final QueueFile queueFile;
//    private final byte[] buffer;
//
//    public BackedObjectQueue(QueueFile queueFile, Converter<T> converter) {
//        this.queueFile = queueFile;
//        this.converter = converter;
//        this.buffer = new byte[4096];
//    }
//
//    public int size() {
//        return this.queueFile.size();
//    }
//
//    public void add(T entry) throws IOException {
//
//    }
//
//    public T peek() throws IOException {
//        return converter.deserialize(queueFile.peek(), 0);
//    }
//
//    public void remove(int n) throws IOException {
//        queueFile.remove(n);
//    }
//
//    /** Returns {@code true} if this queue contains no entries. */
//    public boolean isEmpty() {
//        return size() == 0;
//    }
//
//    public void close() throws IOException {
//        queueFile.close();
//    }
//
//    @Override
//    public Iterator<T> iterator() {
//        return new Iterator<T>() {
//            private final Iterator<byte[]> subIterator = queueFile.iterator();
//
//            @Override
//            public boolean hasNext() {
//                return subIterator.hasNext();
//            }
//
//            @Override
//            public T next() {
//                return converter.deserialize(subIterator.next(), 0);
//            }
//
//            @Override
//            public void remove() {
//                subIterator.remove();
//            }
//        };
//    }
//
//    public interface Converter<T> {
//        T deserialize(byte[] bytes, int offset);
//        void serialize(T value, byte[] bytes, int offset);
//    }
//}
