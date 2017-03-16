package org.radarcns.android.device;

/** Listen for updates of wearable devices. */
public interface DeviceStatusListener {
    enum Status {
        /** A device is found and the device manager is trying to connect to it. No data is yet received. */
        CONNECTING,
        /** A device was disconnected and will no longer stream data. If this status is passed without an argument, the device manager is no longer active. */
        DISCONNECTED,
        /** A compatible device was found and connected to. Data can now stream in. */
        CONNECTED,
        /** A device manager is scanning for compatible devices. This status is passed without an argument. */
        READY
    }

    /**
     * A device has an updated status.
     *
     * If the status concerns the entire system state, null is
     * passed as deviceManager.
     */
    void deviceStatusUpdated(DeviceManager deviceManager, Status status);

    /**
     * A device was found but it was not compatible.
     *
     * No further action is required, but the user can be informed that the connection has failed.
     * @param name human-readable device name.
     */
    void deviceFailedToConnect(String name);
}
