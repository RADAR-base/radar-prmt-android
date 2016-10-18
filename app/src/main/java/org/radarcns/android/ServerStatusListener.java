package org.radarcns.android;

public interface ServerStatusListener {
    enum Status {
        CONNECTING, CONNECTED, DISCONNECTED, UPLOADING, INACTIVE
    }
    void updateServerStatus(Status status);
}
