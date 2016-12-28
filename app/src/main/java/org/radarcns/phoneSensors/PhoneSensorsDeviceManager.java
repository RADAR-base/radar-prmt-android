package org.radarcns.phoneSensors;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.Telephony;
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
    private final MeasurementTable<PhoneSensorSms> smsTable;
    private final MeasurementTable<PhoneSensorLocation> locationTable;
    private final AvroTopic<MeasurementKey, PhoneSensorBatteryLevel> batteryTopic;

    private final PhoneSensorsDeviceStatus deviceStatus;

    private String deviceName;
    private boolean isRegistered = false;
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private ScheduledFuture<?> callLogReadFuture;
    private ScheduledFuture<?> smsLogReadFuture;
    private final ScheduledExecutorService executor;
    private final long CALL_SMS_LOG_INTERVAL_MS_DEFAULT = 24*60*60 * 1000L;
    private final long LOCATION_NETWORK_INTERVAL_MS_DEFAULT = 1*60 * 1000L;
    private final long LOCATION_GPS_INTERVAL_MS_DEFAULT = 5*60 * 1000L;

    public PhoneSensorsDeviceManager(Context contextIn, DeviceStatusListener phoneService, String groupId, String sourceId, TableDataHandler dataHandler, PhoneSensorsTopics topics) {
        this.dataHandler = dataHandler;
        this.accelerationTable = dataHandler.getCache(topics.getAccelerationTopic());
        this.lightTable = dataHandler.getCache(topics.getLightTopic());
        this.callTable = dataHandler.getCache(topics.getCallTopic());
        this.smsTable = dataHandler.getCache(topics.getSmsTopic());
        this.locationTable = dataHandler.getCache(topics.getLocationTopic());
        this.batteryTopic = topics.getBatteryLevelTopic();

        this.phoneService = phoneService;

        this.context = contextIn;
        sensorManager = null;

        this.deviceStatus = new PhoneSensorsDeviceStatus();
        this.deviceStatus.getId().setUserId(groupId);
        this.deviceStatus.getId().setSourceId(sourceId);

        this.deviceName = android.os.Build.MODEL;
        updateStatus(DeviceStatusListener.Status.READY);

        // Scheduler TODO: run executor with existing thread pool/factory
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

        // Calls and sms, in and outgoing
        setCallLogUpdateRate(CALL_SMS_LOG_INTERVAL_MS_DEFAULT);
        setSmsLogUpdateRate(CALL_SMS_LOG_INTERVAL_MS_DEFAULT);

        // Location
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
//        PhoneSensorsLocationListener locationListener = new PhoneSensorsLocationListener();
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                processLocation(location);
            }
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_NETWORK_INTERVAL_MS_DEFAULT, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_GPS_INTERVAL_MS_DEFAULT, 0, locationListener);
        // Initialize with last known
        processLocation(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
        processLocation(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));

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
                    Cursor c = context.getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC");
                    if (!c.moveToFirst()) {
                        c.close();
                        return;
                    }

                    long now = System.currentTimeMillis();
                    long timeStamp = c.getLong(c.getColumnIndex(CallLog.Calls.DATE));

                    while ((now - timeStamp) <= period_ms) {
                        processCall(timeStamp,
                                    c.getString(c.getColumnIndex(CallLog.Calls.NUMBER)),
                                    c.getFloat(c.getColumnIndex(CallLog.Calls.DURATION)),
                                    c.getInt(c.getColumnIndex(CallLog.Calls.TYPE)));
                        if (!c.moveToNext()) {
                            c.close();
                            return;
                        }
                        timeStamp = c.getLong(c.getColumnIndex(CallLog.Calls.DATE));
                    }
                    c.close();
                } catch (Throwable t) {
                    logger.warn("Error in processing the call log: {}", t.getMessage());
                    t.printStackTrace();
                }
            }
        }, 0, period_ms, TimeUnit.MILLISECONDS);

        logger.info("Call log: listener activated and set to a period of {}", period_ms);
    }

    public final synchronized void setSmsLogUpdateRate(final long period_ms) {
        if (smsLogReadFuture != null) {
            smsLogReadFuture.cancel(false);
        }

        smsLogReadFuture = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    Cursor c = context.getContentResolver().query(Telephony.Sms.CONTENT_URI, null, null, null, Telephony.Sms.DATE + " DESC");
                    if (!c.moveToFirst()) {
                        c.close();
                        return;
                    }

                    long now = System.currentTimeMillis();
                    long timeStamp = c.getLong(c.getColumnIndex(Telephony.Sms.DATE));

                    while ((now - timeStamp) <= period_ms) {
                        processSMS( timeStamp,
                                    c.getString(c.getColumnIndex(Telephony.Sms.ADDRESS)), //this is phone number rather than address
                                    c.getInt(c.getColumnIndex(Telephony.Sms.TYPE)) );
                        if (!c.moveToNext()) {
                            c.close();
                            return;
                        }
                        timeStamp = c.getLong(c.getColumnIndex(Telephony.Sms.DATE));
                    }
                    c.close();
                } catch (Throwable t) {
                    logger.warn("Error in processing the sms log: {}", t.getMessage());
                    t.printStackTrace();
                }
            }
        }, 0, period_ms, TimeUnit.MILLISECONDS);

        logger.info("SMS log: listener activated and set to a period of {}", period_ms);
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

    public void processCall(long eventTimestampMillis, String target, float duration, int typeCode) {
        target = normalizePhoneTarget(target);
        String targetKey = new String(Hex.encodeHex(DigestUtils.sha256(target + deviceStatus.getId().getSourceId())));
        double eventTimestamp = eventTimestampMillis / 1000d;

        // Check whether a newer call has already been stored
        try {
            PhoneSensorCall lastValue = callTable.getRecords(1, "time", "desc").get(0).value;
            if (eventTimestamp <= lastValue.getTime()) {
                logger.info(String.format("Call log already stored this call: %s, %s, %s, %s", target, targetKey, duration, eventTimestamp));
                return;
            }
        } catch (IndexOutOfBoundsException iobe) {
            logger.warn("Call log: could not find any persisted call records");
        }

        // 1 = incoming, 2 = outgoing, 3 is unanswered incoming (missed/rejected/blocked/etc)
        int type;
        switch (typeCode) {
            case CallLog.Calls.INCOMING_TYPE:
                type = 1;
                break;
            case CallLog.Calls.OUTGOING_TYPE:
                type = 2;
                break;
            default:
                type = 3;
        }

        double timestamp = System.currentTimeMillis() / 1000d;
        PhoneSensorCall value = new PhoneSensorCall(eventTimestamp, timestamp, duration, targetKey, type);
        dataHandler.addMeasurement(callTable, deviceStatus.getId(), value);

        logger.info(String.format("Call log: %s, %s, %s, %s, %s, %s", target, targetKey, duration, type, eventTimestamp, timestamp));
    }

    public void processSMS(long eventTimestampMillis, String target, int typeCode) {
        target = normalizePhoneTarget(target);
        String targetKey = new String(Hex.encodeHex(DigestUtils.sha256(target + deviceStatus.getId().getSourceId())));
        double eventTimestamp = eventTimestampMillis / 1000d;

        // Check whether a newer sms has already been stored
        try {
            PhoneSensorSms lastValue = smsTable.getRecords(1, "time", "desc").get(0).value;
            if (eventTimestamp <= lastValue.getTime()) {
                logger.info(String.format("SMS log already stored this sms: %s, %s, %s", target, targetKey, eventTimestamp));
                return;
            }
        } catch (IndexOutOfBoundsException iobe) {
            logger.warn("SMS log: could not find any persisted sms records");
        }

        // 1 = incoming, 2 = outgoing, 3 is not sent (draft/failed/queued)
        int type;
        switch (typeCode) {
            case Telephony.Sms.MESSAGE_TYPE_INBOX:
                type = 1;
                break;
            case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
            case Telephony.Sms.MESSAGE_TYPE_SENT:
                type = 2;
                break;
            default:
                type = 3;
        }

        double timestamp = System.currentTimeMillis() / 1000d;
        PhoneSensorSms value = new PhoneSensorSms(eventTimestamp, timestamp, targetKey, type);
        dataHandler.addMeasurement(smsTable, deviceStatus.getId(), value);

        logger.info(String.format("SMS log: %s, %s, %s, %s, %s", target, targetKey, type, eventTimestamp, timestamp));
    }

    public void processLocation(Location location) {
        double eventTimestamp = location.getTime() / 1000d;
        double timestamp = System.currentTimeMillis() / 1000d;

        int provider;
        switch(location.getProvider()) {
            case LocationManager.GPS_PROVIDER:
                provider = 1;
                break;
            case LocationManager.NETWORK_PROVIDER:
                provider = 2;
                break;
            default:
                provider = 3;
        }

        float altitude = location.hasAltitude() ? (float) location.getAltitude() : Float.NaN;
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : Float.NaN;
        float speed = location.hasSpeed() ? location.getSpeed() : Float.NaN;
        float bearing = location.hasBearing() ? location.getBearing() : Float.NaN;

        PhoneSensorLocation value = new PhoneSensorLocation(
                eventTimestamp, timestamp, provider,
                location.getLatitude(), location.getLongitude(),
                altitude, accuracy, speed, bearing);
        dataHandler.addMeasurement(locationTable, deviceStatus.getId(), value);

        logger.info("Location: {} {} {} {} {} {} {} {} {}",provider,timestamp,location.getLatitude(),location.getLongitude(),accuracy,altitude,speed,bearing,eventTimestamp);
    }

    /**
     * Removes area code from phone number.
     * By returning last 9 characters of input, if input contains more than 9 characters.
     * e.g. +31232014111 becomes 232014111 and 0612345678 becomes 612345678
     * @param phoneTarget String
     * @return String
     */
    public static String normalizePhoneTarget(String phoneTarget) {
        int length = phoneTarget.length();
        if (length <= 9) {
            return phoneTarget;
        }

        return phoneTarget.substring(length-9,length);
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
