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

import org.apache.avro.generic.GenericRecord;
import org.radarcns.android.DataHandler;
import org.radarcns.collect.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class E4DeviceManager implements EmpaDataDelegate, EmpaStatusDelegate {
    private final DataHandler dataHandler;
    private final Topic accelerationTopic;
    private final String groupId;
    private final Context context;
    private final String apiKey;
    private final E4Service e4service;
    private Handler mHandler;
    private final HandlerThread mHandlerThread;
    private String deviceId;
    private final Topic bvpTopic;
    private final Topic batteryTopic;
    private final Topic edaTopic;
    private final Topic ibiTopic;
    private final Topic temperatureTopic;
    private float latestBloodVolumePulse = Float.NaN;
    private float latestBatteryLevel = Float.NaN;
    private float latestElectroDermalActivity = Float.NaN;
    private float[] latestAcceleration = {Float.NaN, Float.NaN, Float.NaN};
    private float latestInterBeatInterval = Float.NaN;
    private float latestTemperature = Float.NaN;
    private EmpaDeviceManager deviceManager;
    private String deviceName;
    private boolean isScanning;
    private final static Logger logger = LoggerFactory.getLogger(E4DeviceManager.class);

    public E4DeviceManager(Context context, E4Service e4Service, String apiKey, String groupId, DataHandler dataHandler, E4Topics topics) {
        this.dataHandler = dataHandler;
        this.accelerationTopic = topics.getAccelerationTopic();
        this.bvpTopic = topics.getBloodVolumePulseTopic();
        this.batteryTopic = topics.getBatteryLevelTopic();
        this.edaTopic = topics.getElectroDermalActivityTopic();
        this.ibiTopic = topics.getInterBeatIntervalTopic();
        this.temperatureTopic = topics.getTemperatureTopic();
        this.e4service = e4Service;

        this.context = context;
        this.apiKey = apiKey;
        deviceManager = null;
        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        this.groupId = groupId;
        this.deviceId = null;
        this.deviceName = null;
        this.mHandlerThread = new HandlerThread("E4 device handler", Process.THREAD_PRIORITY_AUDIO);
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
                        }
                    });
                }
                break;
            case CONNECTED:
                // The device manager has established a connection
                this.deviceManager.stopScanning();
                this.e4service.addDevice(this);
                break;
            case DISCONNECTING:
            case DISCONNECTED:
                // The device manager disconnected from a device. Before it ever makes a connection,
                // it also calls this, so check if we have a connected device first.
                if (deviceName != null) {
                    this.e4service.removeDevice(this);
                    deviceName = null;
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
                            e4service.connectingDevice(E4DeviceManager.this);
                            deviceManager.connectDevice(bluetoothDevice);
                            deviceId = groupId + "-" + bluetoothDevice.getAddress();
                        } catch (ConnectionNotAllowedException e) {
                            // This should happen only if you try to connect when allowed == false.
                            e4service.failedToConnect(deviceName);
                        }
                    }
                });
            }
        } else {
            e4service.failedToConnect(deviceName);
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
            e4service.removeDevice(this);
        }
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        latestAcceleration[0] = x / 64f;
        latestAcceleration[1] = y / 64f;
        latestAcceleration[2] = z / 64f;
        dataHandler.sendAndAddToTable(accelerationTopic, deviceId, timestamp, "x", latestAcceleration[0], "y", latestAcceleration[1], "z", latestAcceleration[2]);
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        latestBloodVolumePulse = bvp;
        dataHandler.sendAndAddToTable(bvpTopic, deviceId, timestamp, "bloodVolumePulse", bvp);
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        latestBatteryLevel = battery;
        GenericRecord record = batteryTopic.createSimpleRecord(timestamp, "batteryLevel", battery);
        dataHandler.trySend(batteryTopic, 0L, deviceId, record);
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        latestElectroDermalActivity = gsr;
        dataHandler.sendAndAddToTable(edaTopic, deviceId, timestamp, "electroDermalActivity", gsr);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        latestInterBeatInterval = ibi;
        dataHandler.sendAndAddToTable(ibiTopic, deviceId, timestamp, "interBeatInterval", ibi);
    }

    @Override
    public void didReceiveTemperature(float temperature, double timestamp) {
        latestTemperature = temperature;
        dataHandler.sendAndAddToTable(temperatureTopic, deviceId, timestamp, "temperature", temperature);
    }

    float getLatestBloodVolumePulse() {
        return latestBloodVolumePulse;
    }

    float getLatestBatteryLevel() {
        return latestBatteryLevel;
    }

    float getLatestElectroDermalActivity() {
        return latestElectroDermalActivity;
    }

    float getLatestInterBeatInterval() {
        return latestInterBeatInterval;
    }

    float getLatestTemperature() {
        return latestTemperature;
    }

    float[] getLatestAcceleration() {
        return this.latestAcceleration;
    }

    String getDeviceName() {
        return deviceName;
    }
}
