package org.radarcns.empaticaE4;

/** Listen for updates of Empatica E4 devices. */
public interface E4DeviceStatusListener {
    enum Status {
        /** An Empatica E4 is found and the device manager is trying to connect to it. No data is yet received. */
        CONNECTING,
        /** An Empatica E4 was disconnected and will no longer stream data. If this status is passed without an argument, the Empatica E4 device manager is no longer active. */
        DISCONNECTED,
        /** A compatible Empatica E4 was found and connected to. Data can now stream in. */
        CONNECTED,
        /** The Empatica E4 device manager is scanning for compatible devices. */
        READY
    }

    /** A device has an updated status.
     *
     * If the status concerns the entire system state, null is
     * passed as deviceManager.
     */
    void deviceStatusUpdated(E4DeviceManager deviceManager, Status status);

    /**
     * A device was found but it was not compatible.
     *
     * No further action is required, but the user can be informed that the connection has failed.
     * @param name human-readable device name.
     */
    void deviceFailedToConnect(String name);
}
