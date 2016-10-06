package org.radarcns.empaticaE4;

interface ServerStatusListener {
    enum Status {
        CONNECTING, CONNECTED, DISCONNECTED, UPLOADING
    }
    void updateStatus(Status status);
}
