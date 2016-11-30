package org.radarcns.pebble2;

import android.content.Intent;

import org.apache.avro.specific.SpecificRecord;
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
 * A service that manages a E4DeviceManager and a TableDataHandler to send store the data of an
 * Empatica E4 and send it to a Kafka REST proxy.
 */
public class Pebble2Service extends DeviceService {
    private final static Logger logger = LoggerFactory.getLogger(Pebble2Service.class);
    private Pebble2Topics topics;
    private String apiKey;
    private String groupId;

    @Override
    public void onCreate() {
        logger.info("Creating E4 service {}", this);
        super.onCreate();

        topics = Pebble2Topics.getInstance();
    }

    @Override
    protected DeviceManager createDeviceManager() {
        return new Pebble2DeviceManager(this, this, apiKey, groupId, getDataHandler(), topics);
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
                topics.getAccelerationTopic(), topics.getBloodVolumePulseTopic(),
                topics.getElectroDermalActivityTopic(), topics.getInterBeatIntervalTopic(),
                topics.getTemperatureTopic(), topics.getSensorStatusTopic()
        };
    }

    @Override
    protected void onInvocation(Intent intent) {
        super.onInvocation(intent);
        if (apiKey == null) {
            apiKey = intent.getStringExtra("empatica_api_key");
            logger.info("Using API key {}", apiKey);
            groupId = intent.getStringExtra("group_id");
        }
    }
}
