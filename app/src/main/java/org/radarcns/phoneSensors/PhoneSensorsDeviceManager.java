package org.radarcns.phoneSensors;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.provider.CallLog;
import android.support.annotation.NonNull;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.MeasurementTable;
import org.radarcns.android.TableDataHandler;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Manages Phone sensors */
public class PhoneSensorsDeviceManager implements DeviceManager, SensorEventListener {
    private final static Logger logger = LoggerFactory.getLogger(PhoneSensorsDeviceManager.class);

    private final TableDataHandler dataHandler;
    private final Context context;

    private final DeviceStatusListener phoneService;

    private Sensor accelerometer;
    private Sensor lightSensor;
    private Intent batteryStatus;

    private final MeasurementTable<PhoneSensorAcceleration> accelerationTable;
    private final MeasurementTable<PhoneSensorLight> lightTable;
    private final MeasurementTable<PhoneSensorCall> callTable;
    private final AvroTopic<MeasurementKey, PhoneSensorBatteryLevel> batteryTopic;

    private final PhoneSensorsDeviceStatus deviceStatus;

    private String deviceName;
    private boolean isRegistered = false;
    private SensorManager sensorManager;
    private ScheduledFuture<?> callLogReadFuture;
    private final ScheduledExecutorService executor;
    private final long CALL_LOG_INTERVAL_MS_DEFAULT = 24*60*60 * 1000L; //60*60*1000; // an hour in milliseconds

    public PhoneSensorsDeviceManager(Context contextIn, DeviceStatusListener phoneService, String groupId, String sourceId, TableDataHandler dataHandler, PhoneSensorsTopics topics) {
        this.dataHandler = dataHandler;
        this.accelerationTable = dataHandler.getCache(topics.getAccelerationTopic());
        this.lightTable = dataHandler.getCache(topics.getLightTopic());
        this.callTable = dataHandler.getCache(topics.getCallTopic());
        this.batteryTopic = topics.getBatteryLevelTopic();

        this.phoneService = phoneService;

        this.context = contextIn;
        sensorManager = null;

        this.deviceStatus = new PhoneSensorsDeviceStatus();
        this.deviceStatus.getId().setUserId(groupId);
        this.deviceStatus.getId().setSourceId(sourceId);

        this.deviceName = android.os.Build.MODEL;
        updateStatus(DeviceStatusListener.Status.READY);

        // Call log
        executor = Executors.newSingleThreadScheduledExecutor();
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
        IntentFilter intentBattery = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = context.registerReceiver(null, intentBattery);

        // Calls, in and outgoing
        setCallLogUpdateRate(CALL_LOG_INTERVAL_MS_DEFAULT);

        isRegistered = true;
        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    public final synchronized void setCallLogUpdateRate(final long period_ms) {
        if (callLogReadFuture != null) {
            callLogReadFuture.cancel(false);
        }

        callLogReadFuture = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    Cursor c = context.getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, null);
                    if (!c.moveToLast()) {
                        c.close();
                        return;
                    }

                    long now = System.currentTimeMillis();
                    long timeStamp = c.getLong(c.getColumnIndex(CallLog.Calls.DATE));
                    //TODO: check for last call record send
                    while ((now - timeStamp) <= period_ms) {
                        processCall(c.getString(c.getColumnIndex(CallLog.Calls.NUMBER)),
                                    c.getFloat(c.getColumnIndex(CallLog.Calls.DURATION)),
                                    c.getInt(c.getColumnIndex(CallLog.Calls.TYPE)),
                                    timeStamp);
                        if (!c.moveToPrevious()) {
                            c.close();
                            return;
                        }
                        timeStamp = c.getLong(c.getColumnIndex(CallLog.Calls.DATE));
                    }
                    c.close();
                } catch (Throwable t) {
                    logger.warn("Error in processing the calls: {}", t.getMessage());
                    t.printStackTrace();
                    return;
                }
            }
        }, 0, period_ms, TimeUnit.MILLISECONDS);

        logger.info("Call log: listener activated and set to a period of {}", period_ms);
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

        dataHandler.addMeasurement(accelerationTable, deviceStatus.getId(), value);
    }

    public void processLight(SensorEvent event) {
        Float lightValue = event.values[0];
        deviceStatus.setLight(lightValue);

        PhoneSensorLight value = new PhoneSensorLight(
                (double) event.timestamp, System.currentTimeMillis() / 1000d,
                lightValue);

        dataHandler.addMeasurement(lightTable, deviceStatus.getId(), value);
    }

    public void processBattery() {
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level / (float)scale;

        deviceStatus.setBatteryLevel(batteryPct);

        double timestamp = System.currentTimeMillis() / 1000d;
        PhoneSensorBatteryLevel value = new PhoneSensorBatteryLevel(timestamp, timestamp, batteryPct);
        dataHandler.trySend(batteryTopic, 0L, deviceStatus.getId(), value);
    }

    public void processCall(String target, float duration, int type, long eventTimestampMillis) {
        String targetKey = new String(Hex.encodeHex(DigestUtils.sha256(target + deviceStatus.getId().getSourceId())));
        double eventTimestamp = eventTimestampMillis / 1000d; // TODO: round to nearest hour

        // TODO: expose some live metric on number of calls or total duration in deviceStatus

        // 1 = incoming, 2 = outgoing, 3 is unanswered incoming (missed/rejected/blocked/etc)
        if (type >= 3) type = 3;

        double timestamp = System.currentTimeMillis() / 1000d;
        PhoneSensorCall value = new PhoneSensorCall(eventTimestamp, timestamp, duration, targetKey, type);
        dataHandler.addMeasurement(callTable, deviceStatus.getId(), value);

        logger.info(String.format("Call log: %s, %s, %s, %s, %s, %s", target, targetKey, duration, type, eventTimestamp, timestamp));
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
        return other == this
                || other != null && getClass().equals(other.getClass())
                && deviceStatus.getId().getSourceId() != null
                && deviceStatus.getId().equals(((PhoneSensorsDeviceManager) other).deviceStatus.getId());
    }

}
