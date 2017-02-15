package org.radarcns.application;

import org.radarcns.android.DeviceTopics;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;

public class ApplicationStatusTopics extends DeviceTopics {

    private final AvroTopic<MeasurementKey, ApplicationServerStatus> serverTopic;
    private final AvroTopic<MeasurementKey, ApplicationRecordCounts> recordstopic;
    private final AvroTopic<MeasurementKey, ApplicationUptime> uptimetopic;

    private static final Object syncObject = new Object();
    private static ApplicationStatusTopics instance = null;

    public static ApplicationStatusTopics getInstance() {
        synchronized (syncObject) {
            if (instance == null) {
                instance = new ApplicationStatusTopics();
            }
            return instance;
        }
    }

    private ApplicationStatusTopics() {
        serverTopic = createTopic("application_server_status",
                ApplicationServerStatus.getClassSchema(),
                ApplicationServerStatus.class);
        recordstopic = createTopic("application_record_counts",
                ApplicationRecordCounts.getClassSchema(),
                ApplicationRecordCounts.class);
        uptimetopic = createTopic("application_uptime",
                ApplicationUptime.getClassSchema(),
                ApplicationUptime.class);
    }

    public AvroTopic<MeasurementKey, ApplicationServerStatus> getServerTopic() {
        return serverTopic;
    }

    public AvroTopic<MeasurementKey, ApplicationRecordCounts> getRecordCountsTopic() {
        return recordstopic;
    }

    public AvroTopic<MeasurementKey, ApplicationUptime> getUptimeTopic() {
        return uptimetopic;
    }

}
