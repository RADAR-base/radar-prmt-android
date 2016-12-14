package org.radarcns.phoneSensors;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.support.annotation.NonNull;

import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.MeasurementTable;
import org.radarcns.android.TableDataHandler;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/** Manages Phone sensors */
class PhoneSensorsDeviceManager implements DeviceManager, SensorEventListener {
    private final static Logger logger = LoggerFactory.getLogger(PhoneSensorsDeviceManager.class);

    private final TableDataHandler dataHandler;
    private final Context context;

    private final DeviceStatusListener phoneService;
    private MeasurementKey deviceId;

    private Sensor accelerometer;
    private Sensor lightSensor;
    private Intent batteryStatus;

    private final MeasurementTable<PhoneSensorAcceleration> accelerationTable;
    private final MeasurementTable<PhoneSensorLight> lightTable;
    private final AvroTopic<MeasurementKey, PhoneSensorBatteryLevel> batteryTopic;

    private final PhoneSensorsDeviceStatus deviceStatus;

    private String deviceName;
    private boolean isRegistered = false;
    private SensorManager sensorManager;

    public PhoneSensorsDeviceManager(Context context, DeviceStatusListener phoneService, String groupId, TableDataHandler dataHandler, PhoneSensorsTopics topics) {
        this.dataHandler = dataHandler;
        this.accelerationTable = dataHandler.getCache(topics.getAccelerationTopic());
        this.lightTable = dataHandler.getCache(topics.getLightTopic());
        this.batteryTopic = topics.getBatteryLevelTopic();

        this.phoneService = phoneService;

        this.context = context;
        sensorManager = null;
        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        this.deviceId = new MeasurementKey();
        this.deviceId.setUserId(groupId);
        this.deviceName = null;
        this.deviceStatus = new PhoneSensorsDeviceStatus();
        this.deviceName = android.os.Build.MODEL;
        updateStatus(DeviceStatusListener.Status.READY);
    }

    @Override
    public void start(@NonNull final Set<String> acceptableIds) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        // Accelerometer
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // success! we have an accelerometer
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            logger.warn("Phone Accelerometer not found");
        }

        // Light
        if (sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            logger.warn("Phone Light sensor not found");
        }

        // Battery
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = context.registerReceiver(null, ifilter);


        isRegistered = true;
        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if ( event.sensor.getType() == Sensor.TYPE_ACCELEROMETER ) {
            processAcceleration(event);
        } else if ( event.sensor.getType() == Sensor.TYPE_LIGHT ) {
            processLight(event);
        } else {
            logger.info("Phone registered other sensor change: '{}'", event.sensor.getType());
        }

        // Get new battery status
        processBattery();
    }

    public void processAcceleration(SensorEvent event) {
        // x,y,z are in m/s2
        Float x = event.values[0] / 9.81f;
        Float y = event.values[1] / 9.81f;
        Float z = event.values[2] / 9.81f;
        deviceStatus.setAcceleration(x, y, z);

        float[] latestAcceleration = deviceStatus.getAcceleration();
        PhoneSensorAcceleration value = new PhoneSensorAcceleration(
                (double) event.timestamp, System.currentTimeMillis() / 1000d,
                latestAcceleration[0], latestAcceleration[1], latestAcceleration[2]);

        dataHandler.addMeasurement(accelerationTable, deviceId, value);

        // TODO: DEBUG setting: Report total acceleration as temperature (in ui)
        float testOutput = (float) Math.sqrt( Math.pow(x,2) + Math.pow(y,2) + Math.pow(z,2) );
        deviceStatus.setTemperature(testOutput);
    }

    public void processLight(SensorEvent event) {
        Float lightValue = event.values[0];
        deviceStatus.setLight(lightValue);

        PhoneSensorLight value = new PhoneSensorLight(
                (double) event.timestamp, System.currentTimeMillis() / 1000d,
                lightValue);

        dataHandler.addMeasurement(lightTable, deviceId, value);
    }

    public void processBattery() {
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level / (float)scale;

        deviceStatus.setBatteryLevel(batteryPct);

        double timestamp = System.currentTimeMillis() / 1000d;
        PhoneSensorBatteryLevel value = new PhoneSensorBatteryLevel(timestamp, timestamp, batteryPct);
        dataHandler.trySend(batteryTopic, 0L, deviceId, value);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public boolean isClosed() {
        return !isRegistered;
    }


    @Override
    public void close() {
        sensorManager.unregisterListener(this);
        isRegistered = false;
        updateStatus(DeviceStatusListener.Status.DISCONNECTED);
    }

    @Override
    public String getName() {
        return deviceName;
    }

    @Override
    public PhoneSensorsDeviceStatus getState() {
        return deviceStatus;
    }

    private synchronized void updateStatus(DeviceStatusListener.Status status) {
        this.deviceStatus.setStatus(status);
        this.phoneService.deviceStatusUpdated(this, status);
    }

    @Override
    public boolean equals(Object other) {
        return other == this ||
                other != null && getClass().equals(other.getClass()) &&
                        deviceId.getSourceId() != null && deviceId.equals(((PhoneSensorsDeviceManager) other).deviceId);
    }

}
