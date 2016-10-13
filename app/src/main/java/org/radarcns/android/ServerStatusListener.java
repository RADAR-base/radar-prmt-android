package org.radarcns.android;

public interface ServerStatusListener {
    enum Status {
        CONNECTING, CONNECTED, DISCONNECTED, UPLOADING
    }
    void updateServerStatus(Status status);
}
