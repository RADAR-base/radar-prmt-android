package org.radarcns.kafka.rest;

import java.util.Map;
import java.util.TreeMap;

public interface ServerStatusListener {
    enum Status {
        CONNECTING, CONNECTED, DISCONNECTED, UPLOADING, DISABLED, READY, UPLOADING_FAILED
    }

    // Besides status, also store the actual sent data.
    Map<String, Integer> lastNumberOfRecordsSent = new TreeMap<>();

    void updateServerStatus(Status status);

    void updateRecordsSent(String topicName, int numberOfRecords);
}
