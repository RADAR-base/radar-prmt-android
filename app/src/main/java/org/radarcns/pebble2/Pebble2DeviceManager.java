package org.radarcns.pebble2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.getpebble.android.kit.PebbleKit;

import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.MeasurementTable;
import org.radarcns.android.TableDataHandler;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;
import org.radarcns.util.Serialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;

import static com.getpebble.android.kit.Constants.INTENT_PEBBLE_CONNECTED;
import static com.getpebble.android.kit.Constants.INTENT_PEBBLE_DISCONNECTED;

/** Manages scanning for an Pebble 2 wearable and connecting to it */
class Pebble2DeviceManager implements DeviceManager {
    private final static UUID APP_UUID = UUID.fromString("a3b06265-d50c-4205-8ee4-e4c12abca326");
    private final static int ACCELERATION_LOG = 1;
    private final static int HEART_RATE_LOG = 2;
    private final static int HEART_RATE_FILTERED_LOG = 3;
    private final static int BATTERY_LEVEL_LOG = 4;

    private final static Logger logger = LoggerFactory.getLogger(Pebble2DeviceManager.class);

    private final TableDataHandler dataHandler;
    private final Context context;

    private final DeviceStatusListener pebble2Service;
    private final BroadcastReceiver connectReceiver;
    private final BroadcastReceiver disconnectReceiver;
    private final MeasurementKey deviceId;
    private final PebbleKit.PebbleDataLogReceiver dataLogReceiver;

    private final MeasurementTable<Pebble2Acceleration> accelerationTable;
    private final MeasurementTable<Pebble2HeartRate> heartRateTable;
    private final MeasurementTable<Pebble2HeartRateFiltered> heartRateFilteredTable;
    private final AvroTopic<MeasurementKey, Pebble2BatteryLevel> batteryTopic;

    private final Pebble2DeviceStatus deviceStatus;

    private final String deviceName;
    private boolean isClosed;

    public Pebble2DeviceManager(Context context, DeviceStatusListener pebble2Service, String groupId, TableDataHandler handler, Pebble2Topics topics) {
        this.dataHandler = handler;
        this.accelerationTable = dataHandler.getCache(topics.getAccelerationTopic());
        this.heartRateTable = dataHandler.getCache(topics.getHeartRateTopic());
        this.heartRateFilteredTable = dataHandler.getCache(topics.getHeartRateFilteredTopic());
        this.batteryTopic = topics.getBatteryLevelTopic();

        this.pebble2Service = pebble2Service;
        this.context = context;
        this.deviceName = "pebble2";
        this.deviceId = new MeasurementKey(groupId, this.deviceName);

        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        synchronized (this) {
            this.deviceStatus = new Pebble2DeviceStatus();
            this.isClosed = true;
        }
        this.dataLogReceiver = new PebbleKit.PebbleDataLogReceiver(APP_UUID) {
            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp,
                                    Long tag, byte[] data) {
                double time = Serialization.bytesToLong(data, 0) / 1000d;
                double timeReceived = System.currentTimeMillis() / 1000d;
                switch (tag.intValue()) {
                    case ACCELERATION_LOG:
                        for (int i = 0; i < 25; i++) {
                            int base = i*14;
                            long timeLong = Serialization.bytesToLong(data, base);
                            if (timeLong == 0L) {
                                continue;
                            }
                            time = timeLong / 1000d;
                            float x = Serialization.bytesToShort(data, base + 8) / 1000f;
                            float y = Serialization.bytesToShort(data, base + 10) / 1000f;
                            float z = Serialization.bytesToShort(data, base + 12) / 1000f;
                            dataHandler.addMeasurement(accelerationTable, getDeviceId(), new Pebble2Acceleration(time, timeReceived, x, y, z));
                        }
                        break;
                    case HEART_RATE_LOG:
                        float heartRate = Serialization.bytesToInt(data, 8);
                        dataHandler.addMeasurement(heartRateTable, getDeviceId(), new Pebble2HeartRate(time, timeReceived, heartRate));
                        break;
                    case HEART_RATE_FILTERED_LOG:
                        float heartRateFiltered = Serialization.bytesToInt(data, 8);
                        dataHandler.addMeasurement(heartRateFilteredTable, getDeviceId(), new Pebble2HeartRateFiltered(time, timeReceived, heartRateFiltered));
                        break;
                    case BATTERY_LEVEL_LOG:
                        float batteryLevel = data[8] / 100f;
                        boolean isCharging = data[9] == 1;
                        boolean isPluggedIn = data[10] == 1;
                        dataHandler.trySend(batteryTopic, 0L, getDeviceId(), new Pebble2BatteryLevel(time, timeReceived, batteryLevel, isCharging, isPluggedIn));
                        break;
                }
            }

            @Override
            public void onFinishSession(Context context, UUID logUuid, Long timestamp,
                                        Long tag) {
                logger.info("Pebble 2 session finished {}", deviceName);
            }

        };
        this.connectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(INTENT_PEBBLE_CONNECTED)) {
                    logger.info("Pebble connected with intent {}", Serialization.bundleToString(intent.getExtras()));
                    updateStatus(DeviceStatusListener.Status.CONNECTED);
                }
            }
        };
        this.disconnectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(INTENT_PEBBLE_DISCONNECTED)) {
                    logger.info("Pebble disconnected with intent {}", Serialization.bundleToString(intent.getExtras()));
                    updateStatus(DeviceStatusListener.Status.DISCONNECTED);
                }
            }
        };

        updateStatus(DeviceStatusListener.Status.DISCONNECTED);
    }

    @Override
    public void start(@NonNull final Set<String> acceptableIds) {
        synchronized (this) {
            if (!isClosed) {
                return;
            }
            this.isClosed = false;
        }
        logger.info("Registering Pebble2 receivers");
        PebbleKit.registerDataLogReceiver(context, dataLogReceiver);
        PebbleKit.registerPebbleConnectedReceiver(context, connectReceiver);
        PebbleKit.registerPebbleDisconnectedReceiver(context, disconnectReceiver);
        updateStatus(DeviceStatusListener.Status.READY);
    }

    @Override
    public synchronized boolean isClosed() {
        return isClosed;
    }

    @Override
    public Pebble2DeviceStatus getState() {
        return deviceStatus;
    }

    @Override
    public void close() {
        logger.info("Closing device {}", deviceName);
        synchronized (this) {
            if (this.isClosed) {
                return;
            }
            this.isClosed = true;
        }
        context.unregisterReceiver(dataLogReceiver);
        context.unregisterReceiver(connectReceiver);
        context.unregisterReceiver(disconnectReceiver);
        updateStatus(DeviceStatusListener.Status.DISCONNECTED);
    }

    private synchronized MeasurementKey getDeviceId() {
        return deviceId;
    }

    @Override
    public synchronized String getName() {
        return deviceName;
    }

    @Override
    public synchronized boolean equals(Object other) {
        return other == this ||
                other != null && getClass().equals(other.getClass()) &&
                deviceId.getSourceId() != null && deviceId.equals(((Pebble2DeviceManager) other).deviceId);
    }

    private synchronized void updateStatus(DeviceStatusListener.Status status) {
        this.deviceStatus.setStatus(status);
        this.pebble2Service.deviceStatusUpdated(this, status);
    }
}
