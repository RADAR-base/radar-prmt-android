package org.radarcns.android.kafka;

public interface ServerStatusListener {
    enum Status {
        CONNECTING, CONNECTED, DISCONNECTED, UPLOADING, DISABLED, READY, serverStatus, UPLOADING_FAILED
    }

    void updateServerStatus(Status status);

    void updateRecordsSent(String topicName, int numberOfRecords);
}
