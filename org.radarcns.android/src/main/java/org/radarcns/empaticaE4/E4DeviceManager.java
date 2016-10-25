package org.radarcns.empaticaE4;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.radarcns.android.TableDataHandler;
import org.radarcns.android.MeasurementTable;
import org.radarcns.kafka.AvroTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class E4DeviceManager implements EmpaDataDelegate, EmpaStatusDelegate {
    private final static Logger logger = LoggerFactory.getLogger(E4DeviceManager.class);

    private final TableDataHandler dataHandler;
    private final String groupId;
    private final Context context;
    private final String apiKey;
    private final E4DeviceStatusListener e4service;
    private final Schema.Field xField, yField, zField;
    private final Schema.Field batteryLevelField;
    private final Schema.Field bloodVolumePulseField;
    private final Schema.Field electroDermalActivityField;
    private final HandlerThread e4deviceThread;
    private Handler mHandler;
    private final HandlerThread mHandlerThread;
    private String deviceId;

    private final MeasurementTable accelerationTable;
    private final MeasurementTable bvpTable;
    private final MeasurementTable edaTable;
    private final MeasurementTable ibiTable;
    private final MeasurementTable temperatureTable;
    private final AvroTopic batteryTopic;

    private float latestBloodVolumePulse = Float.NaN;
    private float latestBatteryLevel = Float.NaN;
    private float latestElectroDermalActivity = Float.NaN;
    private float[] latestAcceleration = {Float.NaN, Float.NaN, Float.NaN};
    private float latestInterBeatInterval = Float.NaN;
    private float latestTemperature = Float.NaN;
    private EmpaDeviceManager deviceManager;
    private String deviceName;
    private boolean isScanning;
    private boolean isDisconnected;
    private Schema.Field interBeatIntervalField;
    private Schema.Field temperatureField;
    private E4DeviceStatusListener.Status status;

    public E4DeviceManager(Context context, E4DeviceStatusListener e4Service, String apiKey, String groupId, TableDataHandler dataHandler, E4Topics topics) {
        this.dataHandler = dataHandler;
        this.accelerationTable = dataHandler.getCache(topics.getAccelerationTopic());
        this.bvpTable = dataHandler.getCache(topics.getBloodVolumePulseTopic());
        this.edaTable = dataHandler.getCache(topics.getElectroDermalActivityTopic());
        this.ibiTable = dataHandler.getCache(topics.getInterBeatIntervalTopic());
        this.temperatureTable = dataHandler.getCache(topics.getTemperatureTopic());
        this.batteryTopic = topics.getBatteryLevelTopic();
        xField = accelerationTable.getTopic().getValueField("x");
        yField = accelerationTable.getTopic().getValueField("y");
        zField = accelerationTable.getTopic().getValueField("z");
        batteryLevelField = batteryTopic.getValueField("batteryLevel");
        bloodVolumePulseField = bvpTable.getTopic().getValueField("bloodVolumePulse");
        electroDermalActivityField = edaTable.getTopic().getValueField("electroDermalActivity");
        interBeatIntervalField = ibiTable.getTopic().getValueField("interBeatInterval");
        temperatureField = temperatureTable.getTopic().getValueField("temperature");

        this.e4service = e4Service;

        this.context = context;
        this.apiKey = apiKey;
        deviceManager = null;
        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        this.groupId = groupId;
        this.deviceId = null;
        this.deviceName = null;
        this.mHandlerThread = new HandlerThread("E4-device-handler", Process.THREAD_PRIORITY_AUDIO);
        this.e4deviceThread = new HandlerThread("E4-device-looper", Process.THREAD_PRIORITY_AUDIO);
        this.isDisconnected = false;
        this.isScanning = false;
    }

    void start() {
        this.mHandlerThread.start();
        this.e4deviceThread.start();
        synchronized (this) {
            this.mHandler = new Handler(this.mHandlerThread.getLooper());
        }
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                // Create a new EmpaDeviceManager. E4DeviceManager is both its data and status delegate.
                deviceManager = new EmpaDeviceManager(new ContextWrapper(context) {
                    @Override
                    public Looper getMainLooper() {
                        return e4deviceThread.getLooper();
                    }
                }, E4DeviceManager.this, E4DeviceManager.this);
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
                            deviceId = groupId + "-" + bluetoothDevice.getAddress();
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
        this.e4deviceThread.quitSafely();
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
        latestAcceleration[0] = x / 64f;
        latestAcceleration[1] = y / 64f;
        latestAcceleration[2] = z / 64f;
        dataHandler.addMeasurement(accelerationTable, deviceId, timestamp, xField, latestAcceleration[0], yField, latestAcceleration[1], zField, latestAcceleration[2]);
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        latestBloodVolumePulse = bvp;
        dataHandler.addMeasurement(bvpTable, deviceId, timestamp, bloodVolumePulseField, bvp);
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        latestBatteryLevel = battery;
        GenericRecord record = batteryTopic.createValueRecord(timestamp, batteryLevelField, battery);
        dataHandler.trySend(batteryTopic, 0L, deviceId, record);
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        latestElectroDermalActivity = gsr;
        dataHandler.addMeasurement(edaTable, deviceId, timestamp, electroDermalActivityField, gsr);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        latestInterBeatInterval = ibi;
        dataHandler.addMeasurement(ibiTable, deviceId, timestamp, interBeatIntervalField, ibi);
    }

    @Override
    public void didReceiveTemperature(float temperature, double timestamp) {
        latestTemperature = temperature;
        dataHandler.addMeasurement(temperatureTable, deviceId, timestamp, temperatureField, temperature);
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

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (other == null || !getClass().equals(other.getClass())) return false;

        return deviceId != null && deviceId.equals(((E4DeviceManager)other).deviceId);
    }

    private synchronized void updateStatus(E4DeviceStatusListener.Status status) {
        this.status = status;
        this.e4service.deviceStatusUpdated(this, status);
    }

    public synchronized E4DeviceStatusListener.Status getStatus() {
        return status;
    }
}
