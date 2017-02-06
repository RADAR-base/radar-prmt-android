package org.radarcns.applicationstatus;

import org.radarcns.android.DeviceTopics;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;

public class ApplicationStatusTopics extends DeviceTopics {

    private final AvroTopic<MeasurementKey, ApplicationStatusServer> serverTopic;
    private final AvroTopic<MeasurementKey, ApplicationStatusRecordCounts> recordstopic;
    private final AvroTopic<MeasurementKey, ApplicationStatusUptime> uptimetopic;

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
        serverTopic = createTopic("application_status_server",
                ApplicationStatusServer.getClassSchema(),
                ApplicationStatusServer.class);
        recordstopic = createTopic("application_status_record_counts",
                ApplicationStatusRecordCounts.getClassSchema(),
                ApplicationStatusRecordCounts.class);
        uptimetopic = createTopic("application_status_uptime",
                ApplicationStatusUptime.getClassSchema(),
                ApplicationStatusUptime.class);
    }

    public AvroTopic<MeasurementKey, ApplicationStatusServer> getServerTopic() {
        return serverTopic;
    }

    public AvroTopic<MeasurementKey, ApplicationStatusRecordCounts> getRecordCountsTopic() {
        return recordstopic;
    }

    public AvroTopic<MeasurementKey, ApplicationStatusUptime> getUptimeTopic() {
        return uptimetopic;
    }

}
