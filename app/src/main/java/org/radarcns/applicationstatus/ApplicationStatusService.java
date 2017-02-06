package org.radarcns.applicationstatus;

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.RadarConfiguration;
import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceService;
import org.radarcns.android.DeviceState;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.DeviceTopics;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;
import org.radarcns.phonesensors.PhoneSensorsService;
import org.radarcns.util.PersistentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

import static org.radarcns.RadarConfiguration.DEFAULT_GROUP_ID_KEY;


public class ApplicationStatusService extends PhoneSensorsService {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationStatusService.class);
    private static final String SOURCE_ID_KEY = "source.id";
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
    protected DeviceState getDefaultState() {
        ApplicationStatusState newStatus = new ApplicationStatusState();
        newStatus.setStatus(DeviceStatusListener.Status.CONNECTED);
        return newStatus;
    }

    @Override
    protected DeviceTopics getTopics() {
        return topics;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AvroTopic<MeasurementKey, ? extends SpecificRecord>[] getCachedTopics() {
        return new AvroTopic[] {
                topics.getServerTopic(), topics.getRecordCountsTopic(), topics.getUptimeTopic()
        };
    }

    @Override
    protected void onInvocation(Bundle bundle) {
        super.onInvocation(bundle);
        if (groupId == null) {
            groupId = RadarConfiguration.getStringExtra(bundle, DEFAULT_GROUP_ID_KEY);
        }
    }
}
