package org.radarcns.data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TimedInt {
    private final AtomicInteger value = new AtomicInteger();
    private final AtomicLong time = new AtomicLong(-1L);

    public int getValue() {
        return value.get();
    }

    public long getTime() {
        return time.get();
    }

    public void add(int delta) {
        value.addAndGet(delta);
        time.set(System.currentTimeMillis());
    }

    public void set(int value) {
        this.value.set(value);
        time.set(System.currentTimeMillis());
    }
}
