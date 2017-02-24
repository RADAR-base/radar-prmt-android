package org.radarcns.biovotionVSM;

import android.os.Bundle;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.RadarConfiguration;
import org.radarcns.android.BaseDeviceState;
import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceService;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.DeviceTopics;
import org.radarcns.topic.AvroTopic;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.radarcns.RadarConfiguration.DEFAULT_GROUP_ID_KEY;

/**
 * A service that manages a BiovotionDeviceManager and a TableDataHandler to send store the data of a
 * Biovotion VSM and send it to a Kafka REST proxy.
 */
public class BiovotionService extends DeviceService {
    private static final Logger logger = LoggerFactory.getLogger(BiovotionService.class);
    private BiovotionTopics topics;
    private String groupId;

    @Override
    public void onCreate() {
        logger.info("Creating Biovotion VSM service {}", this);
        super.onCreate();

        topics = BiovotionTopics.getInstance();
    }

    @Override
    protected DeviceManager createDeviceManager() {
        return new BiovotionDeviceManager(this, this, groupId, getDataHandler(), topics);
    }

    @Override
    protected BaseDeviceState getDefaultState() {
        BiovotionDeviceStatus newStatus = new BiovotionDeviceStatus();
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
                topics.getBatteryStateTopic(), topics.getBloodPulseWaveTopic(),
                topics.getSpO2Topic(), topics.getHeartRateTopic(), topics.getHrvTopic(),
                topics.getRrTopic(), topics.getEnergyTopic(), topics.getTemperatureTopic(),
                topics.getGsrTopic());
    }

    @Override
    protected void onInvocation(Bundle bundle) {
        super.onInvocation(bundle);
        if (groupId == null) {
            groupId = RadarConfiguration.getStringExtra(bundle, DEFAULT_GROUP_ID_KEY);
        }
    }
}
