package org.radarcns.phone;

import android.content.BroadcastReceiver;
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
import org.radarcns.key.MeasurementKey;
import org.radarcns.topic.AvroTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/** Manages Phone sensors */
class PhoneSensorsManager implements DeviceManager, SensorEventListener {
    private static final Logger logger = LoggerFactory.getLogger(PhoneSensorsManager.class);
    private static final float EARTH_GRAVITATIONAL_ACCELERATION = 9.80665f;

    private final TableDataHandler dataHandler;
    private final Context context;

    private final DeviceStatusListener phoneService;

    private final MeasurementTable<PhoneAcceleration> accelerationTable;
    private final MeasurementTable<PhoneLight> lightTable;
    private final AvroTopic<MeasurementKey, PhoneBatteryLevel> batteryTopic;

    private final PhoneState deviceStatus;

    private final String deviceName;
    private boolean isRegistered = false;
    private SensorManager sensorManager;

    public PhoneSensorsManager(Context context, DeviceStatusListener phoneService, String groupId, String sourceId, TableDataHandler dataHandler, PhoneTopics topics) {
        this.dataHandler = dataHandler;
        this.accelerationTable = dataHandler.getCache(topics.getAccelerationTopic());
        this.lightTable = dataHandler.getCache(topics.getLightTopic());
        this.batteryTopic = topics.getBatteryLevelTopic();

        this.phoneService = phoneService;

        this.context = context;
        sensorManager = null;
        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        this.deviceStatus = new PhoneState();
        this.deviceStatus.getId().setUserId(groupId);
        this.deviceStatus.getId().setSourceId(sourceId);
        this.deviceName = android.os.Build.MODEL;
        updateStatus(DeviceStatusListener.Status.READY);
    }

    @Override
    public void start(@NonNull final Set<String> acceptableIds) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        // Accelerometer
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // success! we have an accelerometer
            Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            logger.warn("Phone Accelerometer not found");
        }

        // Light
        if (sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null) {
            Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            logger.warn("Phone Light sensor not found");
        }

        // Battery
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        processBatteryStatus(context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                    processBatteryStatus(intent);
                }
            }
        }, ifilter));

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
    }

    public void processAcceleration(SensorEvent event) {
        // x,y,z are in m/s2
        float x = event.values[0] / EARTH_GRAVITATIONAL_ACCELERATION;
        float y = event.values[1] / EARTH_GRAVITATIONAL_ACCELERATION;
        float z = event.values[2] / EARTH_GRAVITATIONAL_ACCELERATION;
        deviceStatus.setAcceleration(x, y, z);
        // nanoseconds to seconds
        double time = event.timestamp / 1_000_000_000d;
        double timeReceived = System.currentTimeMillis() / 1_000d;

        PhoneAcceleration value = new PhoneAcceleration(time, timeReceived, x, y, z);

        dataHandler.addMeasurement(accelerationTable, deviceStatus.getId(), value);
    }

    public void processLight(SensorEvent event) {
        float lightValue = event.values[0];
        deviceStatus.setLight(lightValue);
        // nanoseconds to seconds
        double time = event.timestamp / 1_000_000_000d;
        double timeReceived = System.currentTimeMillis() / 1000d;

        PhoneLight value = new PhoneLight(time, timeReceived, lightValue);
        dataHandler.addMeasurement(lightTable, deviceStatus.getId(), value);
    }

    public void processBatteryStatus(Intent intent) {
        if (intent == null) {
            return;
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level / (float)scale;

        deviceStatus.setBatteryLevel(batteryPct);

        double time = System.currentTimeMillis() / 1000d;
        PhoneBatteryLevel value = new PhoneBatteryLevel(time, time, batteryPct);
        dataHandler.trySend(batteryTopic, 0L, deviceStatus.getId(), value);
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
    public PhoneState getState() {
        return deviceStatus;
    }

    private synchronized void updateStatus(DeviceStatusListener.Status status) {
        this.deviceStatus.setStatus(status);
        this.phoneService.deviceStatusUpdated(this, status);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null
                || !getClass().equals(other.getClass())
                || deviceStatus.getId().getSourceId() == null) {
            return false;
        }

        PhoneSensorsManager otherDevice = ((PhoneSensorsManager) other);
        return deviceStatus.getId().equals((otherDevice.deviceStatus.getId()));
    }

    @Override
    public int hashCode() {
        return deviceStatus.getId().hashCode();
    }
}
