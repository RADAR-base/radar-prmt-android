package org.radarcns.kafka.rest;

public interface ServerStatusListener {
    enum Status {
        CONNECTING, CONNECTED, DISCONNECTED, UPLOADING, DISABLED, READY
    }
    void updateServerStatus(Status status);
}
