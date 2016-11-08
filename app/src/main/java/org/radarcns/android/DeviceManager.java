package org.radarcns.android;

import java.io.Closeable;

/** Device manager of a wearable device. */
public interface DeviceManager extends Closeable {

    /** Start scanning and try to connect */
    void start();

    /** Whether the device manager was already closed. */
    boolean isClosed();

    /**
     * Get the state of a wearable device.
     *
     * If no wearable is connected, it returns a state with DeviceStatusListener.Status.DISCONNECTED
     * status.
     * @return device state
     */
    DeviceState getState();

    /**
     * Get the name of a connected wearable device.
     */
    String getName();
}
