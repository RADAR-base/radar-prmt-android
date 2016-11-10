package org.radarcns.empaticaE4;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.MeasurementTable;
import org.radarcns.android.TableDataHandler;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/** Manages scanning for an Empatica E4 wearable and connecting to it */
class E4DeviceManager implements EmpaDataDelegate, EmpaStatusDelegate, DeviceManager {
    private final static Logger logger = LoggerFactory.getLogger(E4DeviceManager.class);

    private final TableDataHandler dataHandler;
    private final Context context;
    private final String apiKey;

    private final DeviceStatusListener e4service;
    private Handler mHandler;
    private final HandlerThread mHandlerThread;
    private MeasurementKey deviceId;

    private final MeasurementTable<EmpaticaE4Acceleration> accelerationTable;
    private final MeasurementTable<EmpaticaE4BloodVolumePulse> bvpTable;
    private final MeasurementTable<EmpaticaE4ElectroDermalActivity> edaTable;
    private final MeasurementTable<EmpaticaE4InterBeatInterval> ibiTable;
    private final MeasurementTable<EmpaticaE4Temperature> temperatureTable;
    private final MeasurementTable<EmpaticaE4SensorStatus> sensorStatusTable;
    private final AvroTopic<MeasurementKey, EmpaticaE4BatteryLevel> batteryTopic;

    private final E4DeviceStatus deviceStatus;

    private EmpaDeviceManager deviceManager;
    private String deviceName;
    private boolean isScanning;
    private boolean isDisconnected;
    private Set<String> acceptableIds;

    public E4DeviceManager(Context context, DeviceStatusListener e4Service, String apiKey, String groupId, TableDataHandler dataHandler, E4Topics topics) {
        this.dataHandler = dataHandler;
        this.accelerationTable = dataHandler.getCache(topics.getAccelerationTopic());
        this.bvpTable = dataHandler.getCache(topics.getBloodVolumePulseTopic());
        this.edaTable = dataHandler.getCache(topics.getElectroDermalActivityTopic());
        this.ibiTable = dataHandler.getCache(topics.getInterBeatIntervalTopic());
        this.temperatureTable = dataHandler.getCache(topics.getTemperatureTopic());
        this.sensorStatusTable = dataHandler.getCache(topics.getSensorStatusTopic());
        this.batteryTopic = topics.getBatteryLevelTopic();

        this.e4service = e4Service;

        this.context = context;
        this.apiKey = apiKey;
        deviceManager = null;
        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        this.deviceId = new MeasurementKey();
        this.deviceId.setUserId(groupId);
        this.deviceName = null;
        this.mHandlerThread = new HandlerThread("E4-device-handler", Process.THREAD_PRIORITY_AUDIO);
        this.isDisconnected = false;
        this.isScanning = false;
        this.acceptableIds = null;
        this.deviceStatus = new E4DeviceStatus();
    }

    @Override
    public void start(@NonNull final Set<String> acceptableIds) {
        this.mHandlerThread.start();
        logger.info("Started scanning");
        synchronized (this) {
            this.mHandler = new Handler(this.mHandlerThread.getLooper());
        }
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                // Create a new EmpaDeviceManager. E4DeviceManager is both its data and status delegate.
                deviceManager = new EmpaDeviceManager(context, E4DeviceManager.this, E4DeviceManager.this);
                // Initialize the Device Manager using your API key. You need to have Internet access at this point.
                deviceManager.authenticateWithAPIKey(apiKey);

                E4DeviceManager.this.acceptableIds = new HashSet<>();
                for (String s : acceptableIds) {
                    E4DeviceManager.this.acceptableIds.add(s.toLowerCase());
                }
                logger.info("Authenticated device manager");
            }
        });
    }

    @Override
    public void didUpdateStatus(EmpaStatus empaStatus) {
        logger.info("Updated E4 status to {}", empaStatus);
        switch (empaStatus) {
            case READY:
                // The device manager is ready for use
                // Start scanning
                Handler localHandler = getHandler();
                if (localHandler != null) {
                    localHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            logger.info("Started scanning");
                            deviceManager.startScanning();
                            isScanning = true;
                            updateStatus(DeviceStatusListener.Status.READY);
                        }
                    });
                }
                break;
            case CONNECTED:
                // The device manager has established a connection
                this.deviceManager.stopScanning();
                updateStatus(DeviceStatusListener.Status.CONNECTED);
                break;
            case DISCONNECTING:
            case DISCONNECTED:
                // The device manager disconnected from a device. Before it ever makes a connection,
                // it also calls this, so check if we have a connected device first.
                if (!isDisconnected && deviceName != null) {
                    updateStatus(DeviceStatusListener.Status.DISCONNECTED);
                    isDisconnected = true;
                }
                break;
        }
    }

    @Override
    public void didDiscoverDevice(final BluetoothDevice bluetoothDevice, final String deviceName, int i, boolean allowed) {
        // Check if the discovered device can be used with your API key. If allowed is always false,
        // the device is not linked with your API key. Please check your developer area at
        // https://www.empatica.com/connect/developer.php
        logger.info("Bluetooth address: {}", bluetoothDevice.getAddress());
        if (allowed) {
            final String sourceId = bluetoothDevice.getAddress();
            boolean isAcceptable = acceptableIds.isEmpty();
            for (String s : acceptableIds) {
                if (deviceName.toLowerCase().contains(s) || sourceId.toLowerCase().contains(s)) {
                    isAcceptable = true;
                    break;
                }
            }
            if (!isAcceptable) {
                logger.info("Device {} with ID {} is not listed in acceptable device IDs", deviceName, sourceId);
                e4service.deviceFailedToConnect(deviceName);
            }
            this.deviceName = deviceName;
            Handler localHandler = getHandler();
            if (localHandler != null) {
                localHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // Connect to the device
                            updateStatus(DeviceStatusListener.Status.CONNECTING);
                            deviceManager.connectDevice(bluetoothDevice);
                            deviceId.setSourceId(sourceId);
                        } catch (ConnectionNotAllowedException e) {
                            // This should happen only if you try to connect when allowed == false.
                            e4service.deviceFailedToConnect(deviceName);
                        }
                    }
                });
            }
        } else {
            e4service.deviceFailedToConnect(deviceName);
        }
    }

    private synchronized Handler getHandler() {
        return this.mHandler;
    }

    @Override
    public boolean isClosed() {
        return getHandler() == null;
    }

    @Override
    public void close() {
        logger.info("Closing device {}", deviceName);
        Handler localHandler;
        synchronized (this) {
            if (mHandler == null) {
                throw new IllegalStateException("Already closed");
            }
            localHandler = mHandler;
            mHandler = null;
        }
        localHandler.post(new Runnable() {
            @Override
            public void run() {
                logger.info("Initiated device {} stop-sequence", deviceName);
                if (isScanning) {
                    deviceManager.stopScanning();
                }
                if (deviceName != null) {
                    deviceManager.disconnect(); //TODO MM: this sometimes invokes nullpointer exception in EmpaLinkBLE (getService)
                }
                deviceManager.cleanUp();
                if (!isDisconnected) {
                    updateStatus(DeviceStatusListener.Status.DISCONNECTED);
                    isDisconnected = true;
                }
            }
        });
        this.mHandlerThread.quitSafely();
    }

    @Override
    public void didRequestEnableBluetooth() {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            logger.warn("Bluetooth is not enabled.");
            updateStatus(DeviceStatusListener.Status.DISCONNECTED);
        }
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        deviceStatus.setAcceleration(x / 64f, y / 64f, z / 64f);
        float[] latestAcceleration = deviceStatus.getAcceleration();
        EmpaticaE4Acceleration value = new EmpaticaE4Acceleration(
                timestamp, System.currentTimeMillis() / 1000d,
                latestAcceleration[0], latestAcceleration[1], latestAcceleration[2]);

        dataHandler.addMeasurement(accelerationTable, deviceId, value);
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        deviceStatus.setBloodVolumePulse(bvp);
        EmpaticaE4BloodVolumePulse value = new EmpaticaE4BloodVolumePulse(timestamp, System.currentTimeMillis() / 1000d, bvp);
        dataHandler.addMeasurement(bvpTable, deviceId, value);
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        deviceStatus.setBatteryLevel(battery);
        EmpaticaE4BatteryLevel value = new EmpaticaE4BatteryLevel(timestamp, System.currentTimeMillis() / 1000d, battery);
        dataHandler.trySend(batteryTopic, 0L, deviceId, value);
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        deviceStatus.setElectroDermalActivity(gsr);
        EmpaticaE4ElectroDermalActivity value = new EmpaticaE4ElectroDermalActivity(timestamp, System.currentTimeMillis() / 1000d, gsr);
        dataHandler.addMeasurement(edaTable, deviceId, value);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        deviceStatus.setInterBeatInterval(ibi);
        EmpaticaE4InterBeatInterval value = new EmpaticaE4InterBeatInterval(timestamp, System.currentTimeMillis() / 1000d, ibi);
        dataHandler.addMeasurement(ibiTable, deviceId, value);
    }

    @Override
    public void didReceiveTemperature(float temperature, double timestamp) {
        deviceStatus.setTemperature(temperature);
        EmpaticaE4Temperature value = new EmpaticaE4Temperature(timestamp, System.currentTimeMillis() / 1000d, temperature);
        dataHandler.addMeasurement(temperatureTable, deviceId, value);
    }

    @Override
    public void didUpdateSensorStatus(EmpaSensorStatus empaSensorStatus, EmpaSensorType empaSensorType) {
        deviceStatus.setSensorStatus(empaSensorType, empaSensorStatus);
        double now = System.currentTimeMillis() / 1000d;
        EmpaticaE4SensorStatus value = new EmpaticaE4SensorStatus(now, now, empaSensorType.name(), empaSensorStatus.name());
        dataHandler.addMeasurement(sensorStatusTable, deviceId, value);
    }

    @Override
    public String getName() {
        return deviceName;
    }

    @Override
    public boolean equals(Object other) {
        return other == this ||
                other != null && getClass().equals(other.getClass()) &&
                deviceId.getSourceId() != null && deviceId.equals(((E4DeviceManager) other).deviceId);
    }

    private synchronized void updateStatus(DeviceStatusListener.Status status) {
        this.deviceStatus.setStatus(status);
        this.e4service.deviceStatusUpdated(this, status);
    }

    @Override
    public E4DeviceStatus getState() {
        return deviceStatus;
    }
}
