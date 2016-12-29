package org.radarcns.empaticaE4;

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

import static org.radarcns.RadarConfiguration.DEVICE_GROUP_ID_KEY;
import static org.radarcns.RadarConfiguration.EMPATICA_API_KEY;

/**
 * A service that manages a E4DeviceManager and a TableDataHandler to send store the data of an
 * Empatica E4 and send it to a Kafka REST proxy.
 */
public class E4Service extends DeviceService {
    private static final Logger logger = LoggerFactory.getLogger(E4Service.class);
    private E4Topics topics;
    private String apiKey;
    private String groupId;

    @Override
    public void onCreate() {
        logger.info("Creating E4 service {}", this);
        super.onCreate();

        topics = E4Topics.getInstance();
    }

    @Override
    protected DeviceManager createDeviceManager() {
        return new E4DeviceManager(this, this, apiKey, groupId, getDataHandler(), topics);
    }

    @Override
    protected DeviceState getDefaultState() {
        E4DeviceStatus newStatus = new E4DeviceStatus();
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
    protected void onInvocation(Bundle bundle) {
        super.onInvocation(bundle);
        if (apiKey == null) {
            apiKey = RadarConfiguration.getStringExtra(bundle, EMPATICA_API_KEY);
            logger.info("Using API key {}", apiKey);
            groupId = RadarConfiguration.getStringExtra(bundle, DEVICE_GROUP_ID_KEY);
        }
    }
}
