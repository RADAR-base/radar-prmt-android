package org.radarcns.data;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/** Pool to prevent too many objects being created and garbage collected. */
public abstract class ObjectPool<V> {
    private final Queue<V> pool;

    public ObjectPool(int capacity) {
        pool = new ArrayBlockingQueue<>(capacity);
    }

    protected abstract V newObject();

    @SuppressWarnings("unchecked")
    public <W extends V> W get() {
        V obj = pool.poll();
        if (obj != null) {
            return (W)obj;
        } else {
            return (W)newObject();
        }
    }

    public void add(V object) {
        pool.offer(object);
    }

    public void clear() {
        pool.clear();
    }
}
