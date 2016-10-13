package org.radarcns.empaticaE4;

public interface E4DeviceStatusListener {
    enum Status {
        CONNECTING, DISCONNECTED, CONNECTED, READY;
    }

    void deviceStatusUpdated(E4DeviceManager deviceManager, Status status);

    void deviceFailedToConnect(String name);
}
