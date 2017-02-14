package org.radarcns.phone;

import android.os.Bundle;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.RadarConfiguration;
import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceService;
import org.radarcns.android.DeviceState;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.DeviceTopics;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;
import org.radarcns.util.PersistentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.radarcns.RadarConfiguration.DEFAULT_GROUP_ID_KEY;
import static org.radarcns.RadarConfiguration.SOURCE_ID_KEY;

/**
 * A service that manages the phone sensor manager and a TableDataHandler to send store the data of
 * the phone sensors and send it to a Kafka REST proxy.
 */
public class PhoneSensorsService extends DeviceService {
    private static final Logger logger = LoggerFactory.getLogger(PhoneSensorsService.class);
    private PhoneTopics topics;
    private String groupId;
    private String sourceId;

    @Override
    public void onCreate() {
        logger.info("Creating Phone Sensor service {}", this);
        super.onCreate();

        topics = PhoneTopics.getInstance();
    }

    @Override
    protected DeviceManager createDeviceManager() {
        return new PhoneSensorsManager(this, this, groupId, getSourceId(), getDataHandler(), topics);
    }

    @Override
    protected DeviceState getDefaultState() {
        PhoneState newStatus = new PhoneState();
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
                topics.getAccelerationTopic(), topics.getLightTopic(),
        };
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
