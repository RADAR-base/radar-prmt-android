package org.radarcns.kafka.rest;

public interface ServerStatusListener {
    enum Status {
        CONNECTING, CONNECTED, DISCONNECTED, UPLOADING, DISABLED, READY, UPLOADING_FAILED
    }
    void updateServerStatus(Status status);
}
