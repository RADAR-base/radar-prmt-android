package org.radarcns.pebble2;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.getpebble.android.kit.PebbleKit;

import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.TableDataHandler;
import org.radarcns.data.DataCache;
import org.radarcns.key.MeasurementKey;
import org.radarcns.pebble.Pebble2Acceleration;
import org.radarcns.pebble.Pebble2BatteryLevel;
import org.radarcns.pebble.Pebble2HeartRate;
import org.radarcns.pebble.Pebble2HeartRateFiltered;
import org.radarcns.topic.AvroTopic;
import org.radarcns.util.BundleSerialization;
import org.radarcns.util.Serialization;
import org.radarcns.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static android.bluetooth.BluetoothProfile.GATT_SERVER;
import static com.getpebble.android.kit.Constants.INTENT_PEBBLE_CONNECTED;
import static com.getpebble.android.kit.Constants.INTENT_PEBBLE_DISCONNECTED;

/** Manages scanning for an Pebble 2 wearable and connecting to it */
class Pebble2DeviceManager implements DeviceManager {
    private static final UUID APP_UUID = UUID.fromString("a3b06265-d50c-4205-8ee4-e4c12abca326");
    private static final int ACCELERATION_LOG = 11;
    private static final int HEART_RATE_LOG = 12;
    private static final int HEART_RATE_FILTERED_LOG = 13;
    private static final int BATTERY_LEVEL_LOG = 14;

    private static final Logger logger = LoggerFactory.getLogger(Pebble2DeviceManager.class);
    private static final Pattern CONTAINS_PEBBLE_PATTERN =
            Strings.containsIgnoreCasePattern("pebble");

    private final TableDataHandler dataHandler;
    private final Context context;

    private final DeviceStatusListener pebble2Service;
    private final BroadcastReceiver connectReceiver;
    private final BroadcastReceiver disconnectReceiver;
    private final PebbleKit.PebbleDataLogReceiver dataLogReceiver;

    private final DataCache<MeasurementKey, Pebble2Acceleration> accelerationTable;
    private final DataCache<MeasurementKey, Pebble2HeartRate> heartRateTable;
    private final DataCache<MeasurementKey, Pebble2HeartRateFiltered> heartRateFilteredTable;
    private final AvroTopic<MeasurementKey, Pebble2BatteryLevel> batteryTopic;

    private final Pebble2DeviceStatus deviceStatus;

    private String deviceName;
    private boolean isClosed;
    private Pattern[] acceptableIds;

    public Pebble2DeviceManager(Context context, DeviceStatusListener pebble2Service, String groupId, TableDataHandler handler, Pebble2Topics topics) {
        this.dataHandler = handler;
        this.accelerationTable = dataHandler.getCache(topics.getAccelerationTopic());
        this.heartRateTable = dataHandler.getCache(topics.getHeartRateTopic());
        this.heartRateFilteredTable = dataHandler.getCache(topics.getHeartRateFilteredTopic());
        this.batteryTopic = topics.getBatteryLevelTopic();

        this.pebble2Service = pebble2Service;
        this.context = context;

        synchronized (this) {
            this.deviceName = null;
            this.deviceStatus = new Pebble2DeviceStatus();
            this.deviceStatus.getId().setUserId(groupId);
            this.isClosed = true;
        }
        this.dataLogReceiver = new PebbleKit.PebbleDataLogReceiver(APP_UUID) {
            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp,
                                    Long tag, byte[] data) {
                updateDeviceId();
                synchronized (Pebble2DeviceManager.this) {
                    if (!currentDeviceIsAcceptable()) {
                        logger.info("Device {} is not acceptable", deviceStatus.getId());
                        return;
                    }
                    if (deviceStatus.getStatus() != DeviceStatusListener.Status.CONNECTED) {
                        updateStatus(DeviceStatusListener.Status.CONNECTED);
                    }
                }
                double time = Serialization.bytesToLong(data, 0) / 1000d;
                double timeReceived = System.currentTimeMillis() / 1000d;
                try {
                    switch (tag.intValue()) {
                        case ACCELERATION_LOG:
                            for (int i = 0; i < data.length; ) {
                                long timeLong = Serialization.bytesToLong(data, i);
                                if (timeLong == 0L) {
                                    break;
                                }
                                i += 8;
                                time = timeLong / 1000d;
                                float x = Serialization.bytesToShort(data, i) / 1000f;
                                i += 2;
                                float y = Serialization.bytesToShort(data, i) / 1000f;
                                i += 2;
                                float z = Serialization.bytesToShort(data, i) / 1000f;
                                i += 2;
                                dataHandler.addMeasurement(accelerationTable, getDeviceId(), new Pebble2Acceleration(time, timeReceived, x, y, z));
                                deviceStatus.setAcceleration(x, y, z);
                            }
                            break;
                        case HEART_RATE_LOG:
                            float heartRate = Serialization.bytesToInt(data, 8);
                            dataHandler.addMeasurement(heartRateTable, getDeviceId(), new Pebble2HeartRate(time, timeReceived, heartRate));
                            deviceStatus.setHeartRate(heartRate);
                            break;
                        case HEART_RATE_FILTERED_LOG:
                            float heartRateFiltered = Serialization.bytesToInt(data, 8);
                            dataHandler.addMeasurement(heartRateFilteredTable, getDeviceId(), new Pebble2HeartRateFiltered(time, timeReceived, heartRateFiltered));
                            deviceStatus.setHeartRateFiltered(heartRateFiltered);
                            break;
                        case BATTERY_LEVEL_LOG:
                            float batteryLevel = data[8] / 100f;
                            boolean isCharging = data[9] == 1;
                            boolean isPluggedIn = data[10] == 1;
                            dataHandler.trySend(batteryTopic, 0L, getDeviceId(), new Pebble2BatteryLevel(time, timeReceived, batteryLevel, isCharging, isPluggedIn));
                            deviceStatus.setBatteryLevel(batteryLevel);
                            deviceStatus.setBatteryIsCharging(isCharging);
                            deviceStatus.setBatteryIsPlugged(isPluggedIn);
                            break;
                        default:
                            logger.warn("Log {} not recognized", tag.intValue());
                    }
                } catch (Exception ex) {
                    logger.error("Failed to add data to state {}", deviceStatus, ex);
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
                    if (intent.hasExtra("address")) {
                        String address = intent.getStringExtra("address").toUpperCase();
                        String name;
                        BluetoothAdapter btAdaptor = BluetoothAdapter.getDefaultAdapter();
                        if (BluetoothAdapter.checkBluetoothAddress(address)) {
                            BluetoothDevice btDevice = btAdaptor.getRemoteDevice(address);
                            address = btDevice.getAddress();
                            name = btDevice.getName();
                        } else {
                            name = address;
                            logger.warn("Pebble device not registered with the BluetoothAdaptor; set to address {}", address);
                        }
                        synchronized (Pebble2DeviceManager.this) {
                            deviceName = name;
                            deviceStatus.getId().setSourceId(address);
                            if (currentDeviceIsAcceptable()) {
                                logger.info("Pebble device {} with address {} connected", name, address);
                            } else {
                                logger.warn("Pebble device {} with address {} not an accepted ID", name, address);
                                deviceName = null;
                                deviceStatus.getId().setSourceId(null);
                            }
                        }
                    }
                    logger.info("Pebble connected with intent {}", BundleSerialization.bundleToString(intent.getExtras()));
                    updateStatus(DeviceStatusListener.Status.CONNECTED);
                }
            }
        };
        this.disconnectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(INTENT_PEBBLE_DISCONNECTED)) {
                    synchronized (this) {
                        deviceStatus.getId().setSourceId(null);
                        deviceName = null;
                    }
                    logger.info("Pebble disconnected with intent {}", BundleSerialization.bundleToString(intent.getExtras()));
                    updateStatus(DeviceStatusListener.Status.DISCONNECTED);
                }
            }
        };

        updateStatus(DeviceStatusListener.Status.DISCONNECTED);
    }

    private void updateDeviceId() {
        synchronized (this) {
            if (deviceName != null) {
                return;
            }
        }

        BluetoothManager btManager = (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);

        for (BluetoothDevice btDevice : btManager.getConnectedDevices(GATT_SERVER)) {
            String name = btDevice.getName();
            if (name == null) {
                continue;
            }
            if (CONTAINS_PEBBLE_PATTERN.matcher(name).find()) {
                synchronized (this) {
                    deviceName = name;
                    deviceStatus.getId().setSourceId(btDevice.getAddress());
                    logger.info("Pebble device set to {} with address {}",
                            deviceName, deviceStatus.getId().getSourceId());

                    if (currentDeviceIsAcceptable()) {
                        return;
                    } else {
                        deviceName = null;
                        deviceStatus.getId().setSourceId(null);
                    }
                }
            }
        }
        logger.info("No connected pebble device found");
    }

    private synchronized boolean currentDeviceIsAcceptable() {
        return this.deviceStatus.getId().getSourceId() != null &&
                (this.acceptableIds.length == 0
                    || Strings.findAny(acceptableIds, deviceName)
                    || Strings.findAny(acceptableIds, deviceStatus.getId().getSourceId()));
    }

    @Override
    public void start(@NonNull final Set<String> acceptableIds) {
        synchronized (this) {
            if (!isClosed) {
                return;
            }
            this.isClosed = false;
            this.acceptableIds = Strings.containsPatterns(acceptableIds);
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
        synchronized (this) {
            logger.info("Closing device {}", deviceName);
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
        return deviceStatus.getId();
    }

    @Override
    public synchronized String getName() {
        return deviceName;
    }

    @Override
    public synchronized boolean equals(Object other) {
        return other == this
                || other != null && getClass().equals(other.getClass())
                && deviceStatus.getId().getSourceId() != null
                && deviceStatus.getId().equals(((Pebble2DeviceManager) other).deviceStatus.getId());
    }

    @Override
    public int hashCode() {
        return deviceStatus.getId().hashCode();
    }

    private synchronized void updateStatus(DeviceStatusListener.Status status) {
        this.deviceStatus.setStatus(status);
        this.pebble2Service.deviceStatusUpdated(this, status);
    }
}
