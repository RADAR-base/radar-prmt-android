package org.radarcns.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/** Pool to prevent too many objects being created and garbage collected. */
public class ListPool {
    private final Queue<List<?>> pool;

    /**
     * Create a fixed-size pool
     * @param capacity size of the pool.
     */
    public ListPool(int capacity) {
        pool = new ArrayBlockingQueue<>(capacity);
    }


    /**
     * Create a new List with given initial values.
     * This implementation creates an ArrayList. Override to create a different type of list.
     */
    protected <V> List<V> newObject(Collection<V> initialValues) {
        return new ArrayList<>(initialValues);
    }

    /**
     * Get a new or cached List
     * @param <V> type in the list
     * @param initialValues initial values in the list
     */
    public <V> List<V> get(Collection<V> initialValues) {
        List obj = pool.poll();
        if (obj != null) {
            @SuppressWarnings("unchecked")
            List<V> value = (List<V>)obj;
            value.addAll(initialValues);
            return value;
        } else {
            return newObject(initialValues);
        }
    }


    /**
     * Add a new list to the pool.
     * The list may not be read or modified after this call.
     * @param list list to add the pool.
     */
    public void add(List<?> list) {
        list.clear();
        pool.offer(list);
    }

    /**
     * Remove all objects from the pool.
     */
    public void clear() {
        pool.clear();
    }
}
