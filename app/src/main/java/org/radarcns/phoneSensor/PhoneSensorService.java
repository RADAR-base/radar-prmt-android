package org.radarcns.phoneSensor;

import android.content.Intent;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceService;
import org.radarcns.android.DeviceState;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.DeviceTopics;
import org.radarcns.empaticaE4.E4Topics;
import org.radarcns.empaticaE4.MainActivity;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A service that manages a E4DeviceManager and a TableDataHandler to send store the data of an
 * Empatica E4 and send it to a Kafka REST proxy.
 */
public class PhoneSensorService extends DeviceService {
    private final static Logger logger = LoggerFactory.getLogger(PhoneSensorService.class);
    private E4Topics topics; //TODO
    private String groupId;

    @Override
    public void onCreate() {
        logger.info("Creating Phone service {}", this);
        super.onCreate();

        topics = E4Topics.getInstance();
    }

    @Override
    protected DeviceManager createDeviceManager() {
        return new PhoneSensorManager(this, this, groupId, getDataHandler(), topics);
    }

    @Override
    protected DeviceState getDefaultState() {
        PhoneSensorStatus newStatus = new PhoneSensorStatus();
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
                topics.getAccelerationTopic(), topics.getBloodVolumePulseTopic(),
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
