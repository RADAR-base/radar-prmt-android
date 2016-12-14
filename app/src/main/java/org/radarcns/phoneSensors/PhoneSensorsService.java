package org.radarcns.phoneSensors;

import android.content.Intent;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceService;
import org.radarcns.android.DeviceState;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.DeviceTopics;
import org.radarcns.MainActivity;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A service that manages the phone sensor manager and a TableDataHandler to send store the data of
 * the phone sensors and send it to a Kafka REST proxy.
 */
public class PhoneSensorsService extends DeviceService {
    private final static Logger logger = LoggerFactory.getLogger(PhoneSensorsService.class);
    private PhoneSensorsTopics topics; //TODO
    private String groupId;

    @Override
    public void onCreate() {
        logger.info("Creating Phone Sensor service {}", this);
        super.onCreate();

        topics = PhoneSensorsTopics.getInstance();
    }

    @Override
    protected DeviceManager createDeviceManager() {
        return new PhoneSensorsDeviceManager(this, this, groupId, getDataHandler(), topics);
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
    protected void onInvocation(Intent intent) {
        super.onInvocation(intent);
        if (groupId == null) {
            groupId = intent.getStringExtra(MainActivity.DEVICE_GROUP_ID_KEY);
        }
    }
}
