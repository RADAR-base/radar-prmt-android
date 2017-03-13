package org.radarcns.android.device;

import android.support.annotation.NonNull;

import java.io.Closeable;
import java.util.Set;

/** Device manager of a wearable device. */
public interface DeviceManager extends Closeable {

    /** Start scanning and try to connect
     * @param acceptableIds IDs that are acceptable to connect to. If empty, no selection is made.
     */
    void start(@NonNull Set<String> acceptableIds);

    /** Whether the device manager was already closed. */
    boolean isClosed();

    /**
     * Get the state of a wearable device.
     *
     * If no wearable is connected, it returns a state with DeviceStatusListener.Status.DISCONNECTED
     * status.
     * @return device state
     */
    BaseDeviceState getState();

    /**
     * Get the name of a connected wearable device.
     */
    String getName();
}
