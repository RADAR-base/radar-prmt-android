package org.radarcns.android;

import java.io.Closeable;

public interface DeviceManager extends Closeable {
    void start();
    boolean isClosed();
    DeviceState getState();
    String getName();
}
