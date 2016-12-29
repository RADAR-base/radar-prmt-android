package org.radarcns.kafka.rest;

import java.util.Map;
import java.util.TreeMap;

public interface ServerStatusListener {
    enum Status {
        CONNECTING, CONNECTED, DISCONNECTED, UPLOADING, DISABLED, READY, serverStatus, UPLOADING_FAILED
    }

    // Besides status, also store the actual number of records send. -1 if failed to send records
    Map<String, Integer> lastNumberOfRecordsSent = new TreeMap<>();

    void updateServerStatus(Status status);

    void updateRecordsSent(String topicName, int numberOfRecords);
}
