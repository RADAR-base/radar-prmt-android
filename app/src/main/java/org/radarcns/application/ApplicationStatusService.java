package org.radarcns.application;

import android.os.Bundle;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.R;
import org.radarcns.RadarConfiguration;
import org.radarcns.android.BaseDeviceState;
import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceService;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.DeviceTopics;
import org.radarcns.key.MeasurementKey;
import org.radarcns.topic.AvroTopic;
import org.radarcns.util.PersistentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.radarcns.RadarConfiguration.DEFAULT_GROUP_ID_KEY;
import static org.radarcns.RadarConfiguration.SOURCE_ID_KEY;


public class ApplicationStatusService extends DeviceService {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationStatusService.class);
    private ApplicationStatusTopics topics;
    private String groupId;
    private String sourceId;

    @Override
    public void onCreate() {
        logger.info("Creating Application Status service {}", this);
        super.onCreate();

        topics = ApplicationStatusTopics.getInstance();
    }

    @Override
    protected DeviceManager createDeviceManager() {
        return new ApplicationStatusManager(this, this, groupId, getSourceId(), getDataHandler(), topics);
    }

    @Override
    protected BaseDeviceState getDefaultState() {
        ApplicationState newStatus = new ApplicationState();
        newStatus.setStatus(DeviceStatusListener.Status.DISCONNECTED);
        return newStatus;
    }

    @Override
    protected DeviceTopics getTopics() {
        return topics;
    }

    @Override
    protected List<AvroTopic<MeasurementKey, ? extends SpecificRecord>> getCachedTopics() {
        return Arrays.<AvroTopic<MeasurementKey, ? extends SpecificRecord>>asList(
                topics.getServerTopic(), topics.getRecordCountsTopic(), topics.getUptimeTopic());
    }

    @Override
    public String getDisplayName() {
        return getString(R.string.applicationServiceDisplayName);
    }

    @Override
    protected void onInvocation(Bundle bundle) {
        super.onInvocation(bundle);
        if (groupId == null) {
            groupId = RadarConfiguration.getStringExtra(bundle, DEFAULT_GROUP_ID_KEY);
        }
    }

    public String getSourceId() {
        if (sourceId == null) {
            sourceId = PersistentStorage.loadOrStoreUUID(getClass(), SOURCE_ID_KEY);
        }
        return sourceId;
    }
}
