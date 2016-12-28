package org.radarcns.phoneSensors;

import android.os.Bundle;
import android.os.Environment;

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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;

import static org.radarcns.RadarConfiguration.DEVICE_GROUP_ID_KEY;

/**
 * A service that manages the phone sensor manager and a TableDataHandler to send store the data of
 * the phone sensors and send it to a Kafka REST proxy.
 */
public class PhoneSensorsService extends DeviceService {
    private final static Logger logger = LoggerFactory.getLogger(PhoneSensorsService.class);
    private PhoneSensorsTopics topics;
    private String groupId;
    private String sourceIdFilename = this.getClass().getName() + "_source_id.txt";
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
                topics.getAccelerationTopic(), topics.getLightTopic(), topics.getCallTopic(), topics.getSmsTopic(), topics.getLocationTopic()
        };
    }

    @Override
    protected void onInvocation(Bundle bundle) {
        super.onInvocation(bundle);
        if (groupId == null) {
            groupId = RadarConfiguration.getStringExtra(bundle, DEVICE_GROUP_ID_KEY);
        }
    }

    public String getSourceId() {
        if (sourceId == null) {
            setSourceId( getSourceIdFromFile(sourceIdFilename) );
        }
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    private String getSourceIdFromFile(String fileName) {
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File sourceIdFile = new File(path, fileName);
        if (!path.mkdirs()) {
            logger.error("'{}' could not be created or already exists.", path.getAbsolutePath());
        }

        String result;
        try {
            if (sourceIdFile.exists()) {
                // Read source id
                BufferedReader reader = new BufferedReader(new FileReader(sourceIdFile));
                result = reader.readLine();
                logger.info("Phone source Id '{}' read from file", result);
                reader.close();
            } else {
                // Create new source id
                result = UUID.randomUUID().toString();
                BufferedWriter writer = new BufferedWriter(new FileWriter(sourceIdFile));
                writer.write(result);
                logger.info("Phone source Id '{}' written to file", result);
                writer.close();
            }
        } catch (IOException ioe) {
            logger.warn("IOException when reading/writing phone source id file. Assigning one-time source id.");
            result = UUID.randomUUID().toString();
        }
        return result;
    }
}
