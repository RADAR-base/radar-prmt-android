package org.radarcns.phoneSensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;

import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.MeasurementTable;
import org.radarcns.android.TableDataHandler;
import org.radarcns.empaticaE4.E4Topics;
import org.radarcns.empaticaE4.EmpaticaE4Acceleration;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/** Manages Phone sensors */
class PhoneSensorManager implements DeviceManager, SensorEventListener {
    private final static Logger logger = LoggerFactory.getLogger(PhoneSensorManager.class);

    private final TableDataHandler dataHandler;
    private final Context context;

    private final DeviceStatusListener phoneService;
    private Handler mHandler;
    private final HandlerThread mHandlerThread;
    private MeasurementKey deviceId;

    private Sensor accelerometer;
    private Sensor lightSensor;
    public float testOutput;
    private final MeasurementTable<EmpaticaE4Acceleration> accelerationTable; //TODO
//    private final MeasurementTable<PhoneLight> lightTable;
//    private final AvroTopic<MeasurementKey, PhoneBatteryLevel> batteryTopic;

    private final PhoneSensorStatus deviceStatus;

    private String deviceName;
    private boolean isScanning;
    private SensorManager sensorManager;

    public PhoneSensorManager(Context context, DeviceStatusListener phoneService, String groupId, TableDataHandler dataHandler, E4Topics topics) {
        this.dataHandler = dataHandler;
        this.accelerationTable = dataHandler.getCache(topics.getAccelerationTopic());
//        this.batteryTopic = topics.getBatteryLevelTopic();
        logger.info("Phone device manager started");
        this.phoneService = phoneService;

        this.context = context;
        sensorManager = null;
        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        this.deviceId = new MeasurementKey();
        this.deviceId.setUserId(groupId);
        this.deviceName = null;
        this.mHandlerThread = new HandlerThread("Phone-device-handler", Process.THREAD_PRIORITY_AUDIO);
        this.isScanning = false;
        this.deviceStatus = new PhoneSensorStatus();
        this.deviceName = "TBD: Phone Model"; // TODO: Phone model as name
        updateStatus(DeviceStatusListener.Status.READY);
    }

    @Override
    public void start(@NonNull final Set<String> acceptableIds) {
        updateStatus(DeviceStatusListener.Status.READY);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // success! we have an accelerometer
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            logger.warn("Accelerometer not found");
        }

        if (sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            logger.warn("Light sensor not found");
        }

        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if ( event.sensor.getType() == Sensor.TYPE_ACCELEROMETER ) {
            logger.info("Accelerometer changed");
            processAcceleration(event);
        } else if ( event.sensor.getType() == Sensor.TYPE_LIGHT ) {
            processLight(event);
        } else {
            // other sensor
        }

    }

    public void processAcceleration(SensorEvent event) {
        Float x = event.values[0];
        Float y = event.values[1];
        Float z = event.values[2];
        deviceStatus.setAcceleration(x / 64f, y / 64f, z / 64f);

        float[] latestAcceleration = deviceStatus.getAcceleration();
        EmpaticaE4Acceleration value = new EmpaticaE4Acceleration(
                (double) event.timestamp, System.currentTimeMillis() / 1000d,
                latestAcceleration[0], latestAcceleration[1], latestAcceleration[2]);

        dataHandler.addMeasurement(accelerationTable, deviceId, value);

        // DEBUG: Report total acceleration as temperature (in ui)
        testOutput = (float) Math.sqrt( Math.pow(x,2) + Math.pow(y,2) + Math.pow(z,2) );
        deviceStatus.setTemperature(testOutput);
    }

    public void processLight(SensorEvent event) {
        Float lightValue = event.values[0];
        deviceStatus.setBatteryLevel(lightValue/180);
        //TODO
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

//    @Override
//    public void didUpdateStatus(EmpaStatus empaStatus) {
//        logger.info("Updated E4 status to {}", empaStatus);
//        switch (empaStatus) {
//            case READY:
//                // The device manager is ready for use
//                // Start scanning
//                Handler localHandler = getHandler();
//                if (localHandler != null) {
//                    localHandler.post(new Runnable() {
//                        @Override
//                        public void run() {
//                            logger.info("Started scanning");
//                            deviceManager.startScanning();
//                            isScanning = true;
//                            updateStatus(DeviceStatusListener.Status.READY);
//                        }
//                    });
//                }
//                break;
//            case CONNECTED:
//                // The device manager has established a connection
//                this.deviceManager.stopScanning();
//                updateStatus(DeviceStatusListener.Status.CONNECTED);
//                break;
//            case DISCONNECTING:
//            case DISCONNECTED:
//                // The device manager disconnected from a device. Before it ever makes a connection,
//                // it also calls this, so check if we have a connected device first.
//                if (deviceStatus.getStatus() != DeviceStatusListener.Status.DISCONNECTED && deviceName != null) {
//                    updateStatus(DeviceStatusListener.Status.DISCONNECTED);
//                }
//                break;
//        }
//    }

    private synchronized Handler getHandler() {
        return this.mHandler;
    }

    @Override
    public boolean isClosed() {
        return getHandler() == null;
    }

    @Override
    public void close() {

    }

//    @Override
//    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
//        deviceStatus.setAcceleration(x / 64f, y / 64f, z / 64f);
//        float[] latestAcceleration = deviceStatus.getAcceleration();
//        EmpaticaE4Acceleration value = new EmpaticaE4Acceleration(
//                timestamp, System.currentTimeMillis() / 1000d,
//                latestAcceleration[0], latestAcceleration[1], latestAcceleration[2]);
//
//        dataHandler.addMeasurement(accelerationTable, deviceId, value);
//    }

    @Override
    public String getName() {
        return deviceName;
    }

    @Override
    public PhoneSensorStatus getState() {
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
                        deviceId.getSourceId() != null && deviceId.equals(((PhoneSensorManager) other).deviceId);
    }

}
