package org.radarcns.pebble2;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A service that manages a Pebble2DeviceManager and a TableDataHandler to send store the data of a
 * Pebble 2 and send it to a Kafka REST proxy.
 */
public class Pebble2Service extends DeviceService {
    private final static Logger logger = LoggerFactory.getLogger(Pebble2Service.class);
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
    protected DeviceState getDefaultState() {
        Pebble2DeviceStatus newStatus = new Pebble2DeviceStatus();
        newStatus.setStatus(DeviceStatusListener.Status.DISCONNECTED);
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
                topics.getAccelerationTopic(), topics.getHeartRateTopic(),
                topics.getHeartRateFilteredTopic()
        };
    }

    @Override
    protected void onInvocation(Bundle bundle) {
        super.onInvocation(bundle);
        if (groupId == null) {
            groupId = RadarConfiguration.getStringExtra(bundle, RadarConfiguration.DEVICE_GROUP_ID_KEY);
        }
    }
}
