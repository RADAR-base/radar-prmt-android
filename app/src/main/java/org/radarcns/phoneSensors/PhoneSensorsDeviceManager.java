package org.radarcns.phoneSensors;

import android.content.BroadcastReceiver;
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
import org.radarcns.android.DeviceState;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.MeasurementTable;
import org.radarcns.android.TableDataHandler;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.key.MeasurementKey;
import org.radarcns.opensmile.SmileJNI;
import org.radarcns.util.IOUtil;
import org.radarcns.util.PersistentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.radarcns.android.DeviceService.SERVER_STATUS_CHANGED;

/** Manages Phone sensors */
public class PhoneSensorsDeviceManager implements DeviceManager, SensorEventListener {
    private static final Logger logger = LoggerFactory.getLogger(PhoneSensorsDeviceManager.class);
    private static final float EARTH_GRAVITATIONAL_ACCELERATION = 9.80665f;

    private final TableDataHandler dataHandler;
    private final Context context;

    private final DeviceStatusListener phoneService;

    private final MeasurementTable<PhoneSensorAcceleration> accelerationTable;
    private final MeasurementTable<PhoneSensorLight> lightTable;
    private final MeasurementTable<PhoneSensorCall> callTable;
    private final MeasurementTable<PhoneSensorSms> smsTable;
    private final MeasurementTable<PhoneSensorLocation> locationTable;
    private final MeasurementTable<PhoneSensorUserInteraction> userInteractionTable;
    private final MeasurementTable<AndroidStatusServer> serverStatusTable;
    private final MeasurementTable<PhoneSensorAudio> audioTable;
    private final AvroTopic<MeasurementKey, PhoneSensorBatteryLevel> batteryTopic;


    private final PhoneSensorsDeviceStatus deviceStatus;

    private final String deviceName;
    private boolean isRegistered = false;
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private ScheduledFuture<?> callLogReadFuture;
    private ScheduledFuture<?> smsLogReadFuture;
    private ScheduledFuture<?> audioReadFuture;
    private final ScheduledExecutorService executor;

    private static final String LATITUDE_REFERENCE = "latitude.reference";
    private static final String LONGITUDE_REFERENCE = "longitude.reference";
    private double latitudeReference = Double.NaN;
    private double longitudeReference = Double.NaN;

    int previousServerState;

    private final long CALL_SMS_LOG_INTERVAL_DEFAULT = 24*60*60;
    private final long LOCATION_NETWORK_INTERVAL_DEFAULT = 10*60;
    private final long AUDIO_DURATION_S = 5;
    private final long AUDIO_REC_RATE_S = 30;
    private final String AUDIO_CONFIG_FILE = "liveinput_android.conf";
    private final long LOCATION_GPS_INTERVAL_DEFAULT = 60*60;

    public PhoneSensorsDeviceManager(Context contextIn, DeviceStatusListener phoneService, String groupId, String sourceId, TableDataHandler dataHandler, PhoneSensorsTopics topics) {
        this.dataHandler = dataHandler;
        this.accelerationTable = dataHandler.getCache(topics.getAccelerationTopic());
        this.lightTable = dataHandler.getCache(topics.getLightTopic());
        this.callTable = dataHandler.getCache(topics.getCallTopic());
        this.smsTable = dataHandler.getCache(topics.getSmsTopic());
        this.audioTable = dataHandler.getCache(topics.getAudioTopic());
        this.locationTable = dataHandler.getCache(topics.getLocationTopic());
        this.userInteractionTable = dataHandler.getCache(topics.getUserInteractionTopic());
        this.batteryTopic = topics.getBatteryLevelTopic();
        this.serverStatusTable = dataHandler.getCache(topics.getAndroidServerStatusTopic());

        this.phoneService = phoneService;

        this.context = contextIn;
        sensorManager = null;
        locationListener  = new LocationListener() {
            public void onLocationChanged(Location location) {
                processLocation(location);
            }
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };

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
        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        processBatteryStatus(context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                    processBatteryStatus(intent);
                }
            }
        }, batteryFilter));

        // Server Status
        IntentFilter serverStatusFilter = new IntentFilter(SERVER_STATUS_CHANGED);
        processServerStatus(context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(SERVER_STATUS_CHANGED)) {
                    processServerStatus(intent);
                }
            }
        }, serverStatusFilter));

        // Calls and sms, in and outgoing
        setCallLogUpdateRate(CALL_SMS_LOG_INTERVAL_DEFAULT);
        setSmsLogUpdateRate(CALL_SMS_LOG_INTERVAL_DEFAULT);

        // Location
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        setLocationUpdateRate(LOCATION_GPS_INTERVAL_DEFAULT, LOCATION_NETWORK_INTERVAL_DEFAULT);

        // Audio recording
        //setAudioUpdateRate(AUDIO_REC_RATE_S,AUDIO_DURATION_S,AUDIO_CONFIG_FILE);

        // Screen active
        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_USER_PRESENT);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_USER_PRESENT) ||
                    intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    processInteractionState(intent);
                }
            }
        }, screenStateFilter);

        isRegistered = true;
        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    public final synchronized void setCallLogUpdateRate(final long period) {
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

                    // Break if call outside period, last call was not processed or no next value.
                    // Assumption is that the calls are sorted on timestamp.
                    boolean isInsidePeriod;
                    boolean lastIsProcessed = true;
                    boolean nextIsAvailable = true;
                    long now = System.currentTimeMillis() / 1000;
                    while (true) {
                        long timeStamp = c.getLong(c.getColumnIndex(CallLog.Calls.DATE)) / 1000;
                        isInsidePeriod = (now - timeStamp) <= period;
                        if (!isInsidePeriod ||
                            !lastIsProcessed ||
                            !nextIsAvailable) {
                            logger.info("Call log: stopped searching for new records");
                            c.close();
                            break;
                        }

                        lastIsProcessed = processCall(timeStamp,
                                    c.getString(c.getColumnIndex(CallLog.Calls.NUMBER)),
                                    c.getFloat(c.getColumnIndex(CallLog.Calls.DURATION)),
                                    c.getInt(c.getColumnIndex(CallLog.Calls.TYPE)));

                        nextIsAvailable = c.moveToNext();
                    }
                } catch (Throwable t) {
                    logger.warn("Error in processing the call log: {}", t.getMessage());
                    t.printStackTrace();
                }
            }
        }, 0, period, TimeUnit.SECONDS);

        logger.info("Call log: listener activated and set to a period of {}", period);
    }

    public final synchronized void setSmsLogUpdateRate(final long period) {
        if (smsLogReadFuture != null) {
            smsLogReadFuture.cancel(false);
        }

        smsLogReadFuture = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    Cursor c = context.getContentResolver().query(Telephony.Sms.CONTENT_URI, null, null, null, Telephony.Sms.DATE + " DESC");
                    if(c==null)
                        return;

                    if (!c.moveToFirst()) {
                        c.close();
                        return;
                    }

                    // Break if sms outside period, last sms was not processed or no next value.
                    // Assumption is that the sms are sorted on timestamp.
                    boolean isInsidePeriod;
                    boolean lastIsProcessed = true;
                    boolean nextIsAvailable = true;
                    long now = System.currentTimeMillis() / 1000;
                    while (true) {
                        long timeStamp = c.getLong(c.getColumnIndex(CallLog.Calls.DATE)) / 1000;
                        isInsidePeriod = (now - timeStamp) <= period;
                        if (!isInsidePeriod ||
                            !lastIsProcessed ||
                            !nextIsAvailable) {
                            logger.info("SMS log: stopped searching for new records");
                            c.close();
                            break;
                        }

                        lastIsProcessed = processSMS(timeStamp,
                                       c.getString(c.getColumnIndex(Telephony.Sms.ADDRESS)),
                                       c.getInt(c.getColumnIndex(Telephony.Sms.TYPE)),
                                       c.getString(c.getColumnIndex(Telephony.Sms.BODY)));

                        nextIsAvailable = c.moveToNext();
                    }
                } catch (Throwable t) {
                    logger.warn("Error in processing the sms log: {}", t.getMessage());
                    t.printStackTrace();
                }
            }
        }, 0, period, TimeUnit.SECONDS);

        logger.info("SMS log: listener activated and set to a period of {}", period);
    }

    public final synchronized void setLocationUpdateRate(final long periodGPS, final long periodNetwork) {
        // Remove updates, if any
        locationManager.removeUpdates(locationListener);

        // Initialize with last known and start listening
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            processLocation(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, periodGPS * 1000, 0, locationListener);
            logger.info("Location GPS listener activated and set to a period of {}", periodGPS);
        } else {
            logger.warn("Location GPS listener not found");
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            processLocation(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, periodNetwork * 1000, 0, locationListener);
            logger.info("Location Network listener activated and set to a period of {}", periodNetwork);
        } else {
            logger.warn("Location Network listener not found");
        }
    }

    public final synchronized void setAudioUpdateRate(final long period, final long duration, final String configFile) {
        if (audioReadFuture != null) {
            audioReadFuture.cancel(false);
        }
        SmileJNI.prepareOpenSMILE(context);
        final String conf = this.context.getCacheDir()+"/"+configFile;//"/liveinput_android.conf";
        final MeasurementKey deviceId = deviceStatus.getId();

        audioReadFuture = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                double time = System.currentTimeMillis() / 1_000d;//event.timestamp / 1_000_000_000d;
                double timeReceived = System.currentTimeMillis() / 1_000d;
                final String dataPath = context.getExternalFilesDir("") + "/audio_"+ (new Date()).getTime()+".bin";
                //openSMILE.clas.SMILExtractJNI(conf,1,dataPath);
                SmileJNI smileJNI = new SmileJNI();
                final double finalTime = time;
                final double finalTimeReceived = timeReceived;
                smileJNI.addListener(new SmileJNI.ThreadListener(){
                    @Override
                    public void onFinishedRecording() {
                        try {
                            String b64 = "";
                            if((new File(dataPath)).exists()) {
                                byte[] b = IOUtil.readFile(dataPath);
                                //final String config = "";
                                b64 = android.util.Base64.encodeToString(b, android.util.Base64.DEFAULT);
                            }
                            else {
                                b64 = "Error: No audio file is recorded!";
                            }
                            PhoneSensorAudio value = new PhoneSensorAudio(finalTime, finalTimeReceived, conf, b64);
                            dataHandler.addMeasurement(audioTable, deviceId, value);
                        }catch (IOException e){
                            e.printStackTrace();
                        }
                    }
                });
                smileJNI.runOpenSMILE(conf,dataPath, duration);
            }
        }, 0, period, TimeUnit.SECONDS);
        logger.info("SMS log: listener activated and set to a period of {}", period);
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

        PhoneSensorAcceleration value = new PhoneSensorAcceleration(time, timeReceived, x, y, z);

        dataHandler.addMeasurement(accelerationTable, deviceStatus.getId(), value);
    }

    public void processLight(SensorEvent event) {
        float lightValue = event.values[0];
        deviceStatus.setLight(lightValue);
        // nanoseconds to seconds
        double time = event.timestamp / 1_000_000_000d;
        double timeReceived = System.currentTimeMillis() / 1000d;

        PhoneSensorLight value = new PhoneSensorLight(time, timeReceived, lightValue);
        dataHandler.addMeasurement(lightTable, deviceStatus.getId(), value);
    }

    public void processBatteryStatus(Intent intent) {
        if (intent == null) {
            return;
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level / (float)scale;

        boolean isPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) > 0;

        deviceStatus.setBatteryLevel(batteryPct);

        double time = System.currentTimeMillis() / 1000d;
        PhoneSensorBatteryLevel value = new PhoneSensorBatteryLevel(time, time, batteryPct, isPlugged);
        dataHandler.trySend(batteryTopic, 0L, deviceStatus.getId(), value);
    }

    public void processServerStatus(Intent intent) {
        if (intent == null) {
            return;
        }
        ServerStatusListener.Status status = ServerStatusListener.Status.values()[intent.getIntExtra(SERVER_STATUS_CHANGED, 0)];

        // Send to server
        int state;
        switch(status) {
            case DISABLED:
            case DISCONNECTED:
                state = 0;
                break;
            case CONNECTED:
            case CONNECTING:
            case READY:
            case UPLOADING:
                state = 1;
                break;
            case UPLOADING_FAILED:
                state = 2;
                break;
            default:
                state = 3;
        }

        // Only report if server state changed
        if (state != previousServerState) {
            double timestamp = System.currentTimeMillis() / 1000d;
            AndroidStatusServer value = new AndroidStatusServer(
                    timestamp, timestamp, state);
            dataHandler.addMeasurement(serverStatusTable, deviceStatus.getId(), value);
        }

        previousServerState = state;
    }

    public boolean processCall(long eventTimestamp, String target, float duration, int typeCode) {
        // Check whether a newer call has already been stored
        try {
            PhoneSensorCall lastValue = callTable.getRecords(1, "time", "desc").get(0).value;
            if (eventTimestamp <= lastValue.getTime()) {
                logger.info("Call log already stored this call: {}, {}, {}", target, duration, eventTimestamp);
                return false;
            }
        } catch (IndexOutOfBoundsException iobe) {
            logger.info("Call log: could not find any persisted call records");
        }

        String targetKey = createTargetHashKey(target);

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
        PhoneSensorCall value = new PhoneSensorCall(
                (double) eventTimestamp, timestamp, duration, targetKey, type);
        dataHandler.addMeasurement(callTable, deviceStatus.getId(), value);

        logger.info("Call log: {}, {}, {}, {}, {}, {}", target, targetKey, duration, type, eventTimestamp, timestamp);
        return true;
    }

    public boolean processSMS(long eventTimestamp, String target, int typeCode, String message) {
        // Check whether a newer sms has already been stored
        try {
            PhoneSensorSms lastValue = smsTable.getRecords(1, "time", "desc").get(0).value;
            if (eventTimestamp <= lastValue.getTime()) {
                logger.info("SMS log already stored this sms: {}, {}", target, eventTimestamp);
                return false;
            }
        } catch (IndexOutOfBoundsException iobe) {
            logger.info("SMS log: could not find any persisted sms records");
        }

        String targetKey = createTargetHashKey(target);

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

        int length = message.length();

        double timestamp = System.currentTimeMillis() / 1000d;
        PhoneSensorSms value = new PhoneSensorSms(
                (double) eventTimestamp, timestamp, targetKey, type, length);
        dataHandler.addMeasurement(smsTable, deviceStatus.getId(), value);

        logger.info("SMS log: {}, {}, {}, {}, {}, {} chars", target, targetKey, type, eventTimestamp, timestamp, length);
        return true;
    }

    /**
     * Extracts last 9 characters and hashes the result with a salt.
     * For phone numbers this means that the area code is removed
     * E.g.:+31232014111 becomes 232014111 and 0612345678 becomes 612345678 (before hashing)
     * @param target String
     * @return String
     */
    public String createTargetHashKey(String target) {
        int length = target.length();
        if (length > 9) {
            target = target.substring(length-9,length);
        }

        return new String(Hex.encodeHex(DigestUtils.sha256(target + deviceStatus.getId().getSourceId())));
    }

    public void processLocation(Location location) {
        if (location == null) {
            return;
        }
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

        // Coordinates in degrees from a new (random) reference point
        double latitude = location.getLatitude() - getLatitudeReference();
        double longitude = location.getLongitude() - getLongitudeReference();

        // Meta data from GPS
        float altitude = location.hasAltitude() ? (float) location.getAltitude() : Float.NaN;
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : Float.NaN;
        float speed = location.hasSpeed() ? location.getSpeed() : Float.NaN;
        float bearing = location.hasBearing() ? location.getBearing() : Float.NaN;

        PhoneSensorLocation value = new PhoneSensorLocation(
                eventTimestamp, timestamp, provider,
                latitude, longitude,
                altitude, accuracy, speed, bearing);
        dataHandler.addMeasurement(locationTable, deviceStatus.getId(), value);

        logger.info("Location: {} {} {} {} {} {} {} {} {}",provider,eventTimestamp,latitude,longitude,accuracy,altitude,speed,bearing,timestamp);
    }

    public void processInteractionState(Intent intent) {
        int state = 1;
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            state = 0;
        }

        double timestamp = System.currentTimeMillis() / 1000d;
        PhoneSensorUserInteraction value = new PhoneSensorUserInteraction(
                timestamp, timestamp, state);
        dataHandler.addMeasurement(userInteractionTable, deviceStatus.getId(), value);

        logger.info("Interaction State: {} {}", timestamp, state);
    }

    public double getLatitudeReference() {
        if (Double.isNaN(latitudeReference)) {
            setLatitudeReference(getLocationReferenceFromFile(LATITUDE_REFERENCE));
        }
        return latitudeReference;
    }

    public void setLatitudeReference(double latitude) {
        latitudeReference = latitude;
    }

    public double getLongitudeReference() {
        if (Double.isNaN(longitudeReference)) {
            setLongitudeReference(getLocationReferenceFromFile(LONGITUDE_REFERENCE));
        }
        return longitudeReference;
    }

    public void setLongitudeReference(double longitude) {
        longitudeReference = longitude;
    }

    /**
     * Generate random number of degrees in the range [-180,180)
     * and persist to storage.
     * If already persisted to storage, retrieve this value.
     * @param key either referencing longitude or latitude
     * @return number of degrees
     */
    private double getLocationReferenceFromFile(String key) {
        Random generator = new Random();
        double degreesRandom = generator.nextDouble() * 360d - 180d;

        Properties defaults = new Properties();
        defaults.setProperty(key, Double.toString(degreesRandom));
        try {
            Properties props = PersistentStorage.loadOrStore(getClass(), defaults);
            return Double.valueOf(props.getProperty(key));
        } catch (IOException ex) {
            logger.error("Failed to retrieve or store persistent location reference. "
                       + "Using a newly generated location reference.", ex);
            return Double.valueOf(defaults.getProperty(key));
        }
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
        if (other == this) {
            return true;
        }
        if (other == null
                || !getClass().equals(other.getClass())
                || deviceStatus.getId().getSourceId() == null) {
            return false;
        }

        PhoneSensorsDeviceManager otherDevice = ((PhoneSensorsDeviceManager) other);
        return deviceStatus.getId().equals((otherDevice.deviceStatus.getId()));
    }

    @Override
    public int hashCode() {
        return deviceStatus.getId().hashCode();
    }
}
