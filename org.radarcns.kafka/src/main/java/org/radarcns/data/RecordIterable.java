package org.radarcns.data;

import java.io.Closeable;

public interface RecordIterable<K, V> extends Iterable<Record<K, V>>, Closeable {
}
