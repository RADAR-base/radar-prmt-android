package org.radarcns.pebble2;

import android.os.Bundle;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.R;
import org.radarcns.RadarConfiguration;
import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceService;
import org.radarcns.android.BaseDeviceState;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.DeviceTopics;
import org.radarcns.key.MeasurementKey;
import org.radarcns.topic.AvroTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.radarcns.RadarConfiguration.DEFAULT_GROUP_ID_KEY;

/**
 * A service that manages a Pebble2DeviceManager and a TableDataHandler to send store the data of a
 * Pebble 2 and send it to a Kafka REST proxy.
 */
public class Pebble2Service extends DeviceService {
    private static final Logger logger = LoggerFactory.getLogger(Pebble2Service.class);
    private Pebble2Topics topics;
    private String groupId;

    @Override
    public void onCreate() {
        logger.info("Creating Pebble2 service {}", this);
        super.onCreate();

        topics = Pebble2Topics.getInstance();
    }

    @Override
    protected DeviceManager createDeviceManager() {
        return new Pebble2DeviceManager(this, this, groupId, getDataHandler(), topics);
    }

    @Override
    protected BaseDeviceState getDefaultState() {
        Pebble2DeviceStatus newStatus = new Pebble2DeviceStatus();
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
                topics.getAccelerationTopic(), topics.getHeartRateTopic(),
                topics.getHeartRateFilteredTopic());
    }

    @Override
    protected void onInvocation(Bundle bundle) {
        super.onInvocation(bundle);
        if (groupId == null) {
            groupId = RadarConfiguration.getStringExtra(bundle, DEFAULT_GROUP_ID_KEY);
        }
    }
}
