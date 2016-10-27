package org.radarcns.empaticaE4;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.android.MeasurementTable;
import org.radarcns.android.TableDataHandler;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class E4DeviceManager implements EmpaDataDelegate, EmpaStatusDelegate {
    private final static Logger logger = LoggerFactory.getLogger(E4DeviceManager.class);

    private final TableDataHandler dataHandler;
    private final Context context;
    private final String apiKey;

    private final E4DeviceStatusListener e4service;
    private Handler mHandler;
    private final HandlerThread mHandlerThread;
    private MeasurementKey deviceId;

    private final MeasurementTable accelerationTable;
    private final MeasurementTable bvpTable;
    private final MeasurementTable edaTable;
    private final MeasurementTable ibiTable;
    private final MeasurementTable temperatureTable;
    private final AvroTopic batteryTopic;

    private final E4DeviceStatus deviceStatus;

    private EmpaDeviceManager deviceManager;
    private String deviceName;
    private boolean isScanning;
    private boolean isDisconnected;

    public E4DeviceManager(Context context, E4DeviceStatusListener e4Service, String apiKey, String groupId, TableDataHandler dataHandler, E4Topics topics) {
        this.dataHandler = dataHandler;
        this.accelerationTable = dataHandler.getCache(topics.getAccelerationTopic());
        this.bvpTable = dataHandler.getCache(topics.getBloodVolumePulseTopic());
        this.edaTable = dataHandler.getCache(topics.getElectroDermalActivityTopic());
        this.ibiTable = dataHandler.getCache(topics.getInterBeatIntervalTopic());
        this.temperatureTable = dataHandler.getCache(topics.getTemperatureTopic());
        this.batteryTopic = topics.getBatteryLevelTopic();

        this.e4service = e4Service;

        this.context = context;
        this.apiKey = apiKey;
        deviceManager = null;
        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        this.deviceId = new MeasurementKey(groupId, null);
        this.deviceName = null;
        this.mHandlerThread = new HandlerThread("E4-device-handler", Process.THREAD_PRIORITY_AUDIO);
        this.isDisconnected = false;
        this.isScanning = false;

        this.deviceStatus = new E4DeviceStatus();
    }

    void start() {
        this.mHandlerThread.start();
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
            }
        });
    }

    @Override
    public void didUpdateStatus(EmpaStatus empaStatus) {
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
                            updateStatus(E4DeviceStatusListener.Status.READY);
                        }
                    });
                }
                break;
            case CONNECTED:
                // The device manager has established a connection
                this.deviceManager.stopScanning();
                this.updateStatus(E4DeviceStatusListener.Status.CONNECTED);
                break;
            case DISCONNECTING:
            case DISCONNECTED:
                // The device manager disconnected from a device. Before it ever makes a connection,
                // it also calls this, so check if we have a connected device first.
                if (!isDisconnected) {
                    this.updateStatus(E4DeviceStatusListener.Status.DISCONNECTED);
                    isDisconnected = true;
                }
                break;
        }
    }

    @Override
    public void didUpdateSensorStatus(EmpaSensorStatus empaSensorStatus, EmpaSensorType empaSensorType) {
    }

    @Override
    public void didDiscoverDevice(final BluetoothDevice bluetoothDevice, final String deviceName, int i, boolean allowed) {
        // Check if the discovered device can be used with your API key. If allowed is always false,
        // the device is not linked with your API key. Please check your developer area at
        // https://www.empatica.com/connect/developer.php
        if (allowed) {
            this.deviceName = deviceName;
            Handler localHandler = getHandler();
            if (localHandler != null) {
                localHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // Connect to the device
                            updateStatus(E4DeviceStatusListener.Status.CONNECTING);
                            deviceManager.connectDevice(bluetoothDevice);
                            deviceId.setDeviceId(bluetoothDevice.getAddress());
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

    public boolean isClosed() {
        return getHandler() == null;
    }

    void close() {
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
                if (isScanning) {
                    deviceManager.stopScanning();
                }
                if (deviceName != null) {
                    deviceManager.disconnect();
                }
                deviceManager.cleanUp();
            }
        });
        this.mHandlerThread.quitSafely();
    }

    @Override
    public void didRequestEnableBluetooth() {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            logger.warn("Bluetooth is not enabled.");
            updateStatus(E4DeviceStatusListener.Status.DISCONNECTED);
        }
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        deviceStatus.setAcceleration(x / 64f, y / 64f, z / 64f);
        float[] latestAcceleration = deviceStatus.getAcceleration();
        SpecificRecord value = new EmpaticaE4Acceleration(
                timestamp, System.currentTimeMillis() / 1000d,
                latestAcceleration[0], latestAcceleration[1], latestAcceleration[2]);

        dataHandler.addMeasurement(accelerationTable, deviceId, value);
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        deviceStatus.setBloodVolumePulse(bvp);
        SpecificRecord value = new EmpaticaE4BloodVolumePulse(timestamp, System.currentTimeMillis() / 1000d, bvp);
        dataHandler.addMeasurement(bvpTable, deviceId, value);
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        deviceStatus.setBatteryLevel(battery);
        SpecificRecord value = new EmpaticaE4BatteryLevel(timestamp, System.currentTimeMillis() / 1000d, battery);
        dataHandler.trySend(batteryTopic, 0L, deviceId, value);
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        deviceStatus.setElectroDermalActivity(gsr);
        SpecificRecord value = new EmpaticaE4ElectroDermalActivity(timestamp, System.currentTimeMillis() / 1000d, gsr);
        dataHandler.addMeasurement(edaTable, deviceId, value);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        deviceStatus.setInterBeatInterval(ibi);
        SpecificRecord value = new EmpaticaE4InterBeatInterval(timestamp, System.currentTimeMillis() / 1000d, ibi);
        dataHandler.addMeasurement(ibiTable, deviceId, value);
    }

    @Override
    public void didReceiveTemperature(float temperature, double timestamp) {
        deviceStatus.setTemperature(temperature);
        SpecificRecord value = new EmpaticaE4Temperature(timestamp, System.currentTimeMillis() / 1000d, temperature);
        dataHandler.addMeasurement(temperatureTable, deviceId, value);
    }

    String getDeviceName() {
        return deviceName;
    }

    @Override
    public boolean equals(Object other) {
        return other == this ||
                other != null && getClass().equals(other.getClass()) &&
                deviceId.getDeviceId() != null && deviceId.equals(((E4DeviceManager) other).deviceId);
    }

    private synchronized void updateStatus(E4DeviceStatusListener.Status status) {
        this.deviceStatus.setStatus(status);
        this.e4service.deviceStatusUpdated(this, status);
    }

    public E4DeviceStatus getStatus() {
        return deviceStatus;
    }
}
