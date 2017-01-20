package org.radarcns.phoneSensors;

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

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

import static org.radarcns.RadarConfiguration.DEFAULT_GROUP_ID_KEY;

/**
 * A service that manages the phone sensor manager and a TableDataHandler to send store the data of
 * the phone sensors and send it to a Kafka REST proxy.
 */
public class PhoneSensorsService extends DeviceService {
    private static final Logger logger = LoggerFactory.getLogger(PhoneSensorsService.class);
    private static final String SOURCE_ID_KEY = "source.id";
    private PhoneSensorsTopics topics;
    private String groupId;
    private String sourceId;

    @Override
    public void onCreate() {
        logger.info("Creating Phone Sensor service {}", this);
        super.onCreate();

        topics = PhoneSensorsTopics.getInstance();
    }

    @Override
    protected DeviceManager createDeviceManager() {
        return new PhoneSensorsDeviceManager(this, this, groupId, getSourceId(), getDataHandler(), topics);
    }

    @Override
    protected DeviceState getDefaultState() {
        PhoneSensorsDeviceStatus newStatus = new PhoneSensorsDeviceStatus();
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
            setSourceId(getSourceIdFromFile());
        }
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    private String getSourceIdFromFile() {
        Properties defaults = new Properties();
        defaults.setProperty(SOURCE_ID_KEY, UUID.randomUUID().toString());
        try {
            Properties props = PersistentStorage.loadOrStore(getClass(), defaults);
            return props.getProperty(SOURCE_ID_KEY);
        } catch (IOException ex) {
            logger.error("Failed to retrieve or store persistent source ID key. "
                    + "Using a newly generated UUID.", ex);
            return defaults.getProperty(SOURCE_ID_KEY);
        }
    }
}
