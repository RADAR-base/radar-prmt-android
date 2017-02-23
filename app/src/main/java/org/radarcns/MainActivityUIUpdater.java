package org.radarcns;

import android.os.RemoteException;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.radarcns.android.BaseDeviceState;
import org.radarcns.android.DeviceServiceConnection;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.data.TimedInt;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.radarcns.RadarConfiguration.CONDENSED_DISPLAY_KEY;

public class MainActivityUIUpdater implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MainActivityUIUpdater.class);
    private static final int NUM_ROWS = 5;
    private static final int MAX_UI_DEVICE_NAME_LENGTH = 25;
    private static final DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    /**
     * Data formats
     **/
    private final DecimalFormat singleDecimal = new DecimalFormat("0.0");
    private final DecimalFormat doubleDecimal = new DecimalFormat("0.00");
    private final DecimalFormat noDecimals = new DecimalFormat("0");
    private final MainActivity mainActivity;
    private final RadarConfiguration radarConfiguration;
    private final BaseDeviceState[] deviceData;
    private final String[] deviceNames;

    private TextView[] mDeviceNameLabels;
    private View[] mStatusIcons;
    private TextView[] mTemperatureLabels;
    private TextView[] mHeartRateLabels;
    private TextView[] mAccelerationLabels;
    private TextView[] mRecordsSentLabels;
    private ImageView[] mBatteryLabels;
    private View mServerStatusIcon;
    private TextView mServerMessage;

    private final Map<ServerStatusListener.Status, Integer> serverStatusIconMap;
    private final static int serverStatusIconDefault = R.drawable.status_disconnected;

    private final Map<DeviceStatusListener.Status, Integer> deviceStatusIconMap;
    private final static int deviceStatusIconDefault = R.drawable.status_searching;

    private String previousTopic;
    private TimedInt previousNumberOfTopicsSent;
    private ServerStatusListener.Status previousServerStatus;
    private DeviceStatusListener.Status[] previousDeviceStatus;
    private String newServerStatus;
    private float[] previousTemperature;
    private float[] previousBatteryLevel;
    private float[] previousHeartRate;
    private float[] previousAcceleration;
    private String previousName;
    private TimedInt[] previousRecordsSent;
    private int[] previousRecordsSentTimer;

    MainActivityUIUpdater(MainActivity activity, RadarConfiguration radarConfiguration) {
        this.radarConfiguration = radarConfiguration;
        this.mainActivity = activity;

        deviceData = new BaseDeviceState[NUM_ROWS];
        deviceNames = new String[NUM_ROWS];

        deviceStatusIconMap = new EnumMap<>(DeviceStatusListener.Status.class);
        deviceStatusIconMap.put(DeviceStatusListener.Status.CONNECTED, R.drawable.status_connected);
        deviceStatusIconMap.put(DeviceStatusListener.Status.DISCONNECTED, R.drawable.status_disconnected);
        deviceStatusIconMap.put(DeviceStatusListener.Status.READY, R.drawable.status_searching);
        deviceStatusIconMap.put(DeviceStatusListener.Status.CONNECTING, R.drawable.status_searching);

        serverStatusIconMap = new EnumMap<>(ServerStatusListener.Status.class);
        serverStatusIconMap.put(ServerStatusListener.Status.CONNECTED, R.drawable.status_connected);
        serverStatusIconMap.put(ServerStatusListener.Status.DISCONNECTED, R.drawable.status_disconnected);
        serverStatusIconMap.put(ServerStatusListener.Status.DISABLED, R.drawable.status_disconnected);
        serverStatusIconMap.put(ServerStatusListener.Status.READY, R.drawable.status_searching);
        serverStatusIconMap.put(ServerStatusListener.Status.CONNECTING, R.drawable.status_searching);
        serverStatusIconMap.put(ServerStatusListener.Status.UPLOADING, R.drawable.status_uploading);
        serverStatusIconMap.put(ServerStatusListener.Status.UPLOADING_FAILED, R.drawable.status_uploading_failed);

        previousDeviceStatus = new DeviceStatusListener.Status[NUM_ROWS];
        previousTemperature = new float[NUM_ROWS];
        previousBatteryLevel = new float[NUM_ROWS];
        previousHeartRate = new float[NUM_ROWS];
        previousRecordsSent = new TimedInt[NUM_ROWS];
        previousRecordsSentTimer = new int[NUM_ROWS];
        previousAcceleration = new float[NUM_ROWS];

        initializeViews();
    }

    public void update() throws RemoteException {
        for (int i = 0; i < NUM_ROWS; i++) {
            DeviceServiceConnection<?> connection = mainActivity.getConnection(i);
            if (connection != null && connection.hasService()) {
                deviceData[i] = connection.getDeviceData();
                switch (deviceData[i].getStatus()) {
                    case CONNECTED:
                    case CONNECTING:
                        deviceNames[i] = connection.getDeviceName();
                        break;
                    default:
                        deviceNames[i] = null;
                        break;
                }
            } else {
                deviceData[i] = null;
                deviceNames[i] = null;
            }
        }
        String message = getServerStatusMessage();
        synchronized (this) {
            newServerStatus = message;
        }
        mainActivity.runOnUiThread(this);
    }

    private String getServerStatusMessage() {
        String topic = mainActivity.getLatestTopicSent();
        TimedInt numberOfRecords = mainActivity.getLatestNumberOfRecordsSent();

        String message = null;
        if (topic != null && (!Objects.equals(topic, previousTopic)
                || !Objects.equals(previousNumberOfTopicsSent, numberOfRecords))) {
            previousTopic = topic;
            previousNumberOfTopicsSent = numberOfRecords;

            // Condensing the message
            topic = topic.replaceFirst("_?android_?", "");
            topic = topic.replaceFirst("_?empatica_?(e4)?", "E4");

            String messageTimeStamp = timeFormat.format(numberOfRecords.getTime());

            if (numberOfRecords.getValue() < 0) {
                message = String.format(Locale.US, "%1$25s has FAILED uploading (%2$s)",
                        topic, messageTimeStamp);
            } else {
                message = String.format(Locale.US, "%1$25s uploaded %2$4d records (%3$s)",
                        topic, numberOfRecords.getValue(), messageTimeStamp);
            }
        }
        return message;
    }

    private void initializeViews() {
        // The columns, fixed to four rows.
        mDeviceNameLabels = new TextView[]{
                (TextView) mainActivity.findViewById(R.id.deviceNameRow1),
                (TextView) mainActivity.findViewById(R.id.deviceNameRow2),
                (TextView) mainActivity.findViewById(R.id.deviceNameRow3),
                (TextView) mainActivity.findViewById(R.id.deviceNameRow4),
                (TextView) mainActivity.findViewById(R.id.deviceNameRow5)
        };

        mStatusIcons = new View[]{
                mainActivity.findViewById(R.id.statusRow1),
                mainActivity.findViewById(R.id.statusRow2),
                mainActivity.findViewById(R.id.statusRow3),
                mainActivity.findViewById(R.id.statusRow4),
                mainActivity.findViewById(R.id.statusRow5)
        };

        mTemperatureLabels = new TextView[]{
                (TextView) mainActivity.findViewById(R.id.temperatureRow1),
                (TextView) mainActivity.findViewById(R.id.temperatureRow2),
                (TextView) mainActivity.findViewById(R.id.temperatureRow3),
                (TextView) mainActivity.findViewById(R.id.temperatureRow4),
                (TextView) mainActivity.findViewById(R.id.temperatureRow5)
        };

        mHeartRateLabels = new TextView[]{
                (TextView) mainActivity.findViewById(R.id.heartRateRow1),
                (TextView) mainActivity.findViewById(R.id.heartRateRow2),
                (TextView) mainActivity.findViewById(R.id.heartRateRow3),
                (TextView) mainActivity.findViewById(R.id.heartRateRow4),
                (TextView) mainActivity.findViewById(R.id.heartRateRow5)
        };

        mAccelerationLabels = new TextView[]{
                (TextView) mainActivity.findViewById(R.id.accelerationRow1),
                (TextView) mainActivity.findViewById(R.id.accelerationRow2),
                (TextView) mainActivity.findViewById(R.id.accelerationRow3),
                (TextView) mainActivity.findViewById(R.id.accelerationRow4),
                (TextView) mainActivity.findViewById(R.id.accelerationRow5)
        };

        mBatteryLabels = new ImageView[]{
                (ImageView) mainActivity.findViewById(R.id.batteryRow1),
                (ImageView) mainActivity.findViewById(R.id.batteryRow2),
                (ImageView) mainActivity.findViewById(R.id.batteryRow3),
                (ImageView) mainActivity.findViewById(R.id.batteryRow4),
                (ImageView) mainActivity.findViewById(R.id.batteryRow5)
        };

        mRecordsSentLabels = new TextView[]{
                (TextView) mainActivity.findViewById(R.id.recordsSentRow1),
                (TextView) mainActivity.findViewById(R.id.recordsSentRow2),
                (TextView) mainActivity.findViewById(R.id.recordsSentRow3),
                (TextView) mainActivity.findViewById(R.id.recordsSentRow4),
                (TextView) mainActivity.findViewById(R.id.recordsSentRow5)
        };

        mServerStatusIcon = mainActivity.findViewById(R.id.statusServer);

        // Server
        mServerMessage = (TextView) mainActivity.findViewById( R.id.statusServerMessage);
    }

    @Override
    public void run() {
        for (int i = 0; i < NUM_ROWS; i++) {
            // Update all fields
            updateDeviceStatus(deviceData[i], i);
            updateTemperature(deviceData[i], i);
            updateHeartRate(deviceData[i], i);
            updateAcceleration(deviceData[i], i);
            updateBattery(deviceData[i], i);
            updateDeviceName(deviceNames[i], i);
            updateDeviceTotalRecordsSent(i);
        }
        updateServerStatus();
    }

    private void updateServerStatus() {
        String message;
        synchronized (this) {
            message = newServerStatus;
        }
        if (message != null) {
            mServerMessage.setText(message);
        }

        ServerStatusListener.Status status = mainActivity.getServerStatus();
        if (!Objects.equals(status, previousServerStatus)) {
            previousServerStatus = status;
            Integer statusIcon = serverStatusIconMap.get(status);
            int resource = statusIcon != null ? statusIcon : serverStatusIconDefault;
            mServerStatusIcon.setBackgroundResource(resource);
        }
    }

    public void updateDeviceStatus(BaseDeviceState deviceData, int row) {
        // Connection status. Change icon used.
        DeviceStatusListener.Status status;
        if (deviceData == null) {
            status = DeviceStatusListener.Status.DISCONNECTED;
        } else {
            status = deviceData.getStatus();
        }
        if (!Objects.equals(status, previousDeviceStatus[row])) {
            logger.info("Device status of row {} is {}", row, status);
            previousDeviceStatus[row] = status;
            Integer statusIcon = deviceStatusIconMap.get(status);
            int resource = statusIcon != null ? statusIcon : deviceStatusIconDefault;
            mStatusIcons[row].setBackgroundResource(resource);
        }
    }

    public void updateTemperature(BaseDeviceState deviceData, int row) {
        // \u2103 == ℃
        setText(mTemperatureLabels, previousTemperature, row,
                deviceData == null ? Float.NaN : deviceData.getTemperature(), "\u2103", singleDecimal);
    }

    public void updateHeartRate(BaseDeviceState deviceData, int row) {
        setText(mHeartRateLabels, previousHeartRate, row,
                deviceData == null ? Float.NaN : deviceData.getHeartRate(), "bpm", noDecimals);
    }

    public void updateAcceleration(BaseDeviceState deviceData, int row) {
        setText(mAccelerationLabels, previousAcceleration, row,
                deviceData == null ? Float.NaN : deviceData.getAccelerationMagnitude(), "g", doubleDecimal);
    }

    public void updateBattery(BaseDeviceState deviceData, int row) {
        // Battery levels observed for E4 are 0.01, 0.1, 0.45 or 1
        float batteryLevel = deviceData == null ? Float.NaN : deviceData.getBatteryLevel();
//            if ( row == 0 ) {logger.info("Battery: {}", batteryLevel);}
        if (previousBatteryLevel[row] == batteryLevel
                || (Float.isNaN(previousBatteryLevel[row]) && Float.isNaN(batteryLevel))) {
            return;
        }
        previousBatteryLevel[row] = batteryLevel;
        if (Float.isNaN(batteryLevel)) {
            mBatteryLabels[row].setImageResource(R.drawable.ic_battery_unknown);
            // up to 100%
        } else if (batteryLevel > 0.5) {
            mBatteryLabels[row].setImageResource(R.drawable.ic_battery_full);
            // up to 45%
        } else if (batteryLevel > 0.2) {
            mBatteryLabels[row].setImageResource(R.drawable.ic_battery_50);
            // up to 10%
        } else if (batteryLevel > 0.1) {
            mBatteryLabels[row].setImageResource(R.drawable.ic_battery_low);
            // up to 5% [what are possible values below 10%?]
        } else {
            mBatteryLabels[row].setImageResource(R.drawable.ic_battery_empty);
        }
    }

    public void updateDeviceName(String deviceName, int row) {
        if (Objects.equals(deviceName, previousName)) {
            return;
        }
        previousName = deviceName;
        // Restrict length of name that is shown.
        if (deviceName != null && deviceName.length() > MAX_UI_DEVICE_NAME_LENGTH - 3) {
            deviceName = deviceName.substring(0, MAX_UI_DEVICE_NAME_LENGTH) + "...";
        }

        // \u2014 == —
        mDeviceNameLabels[row].setText(deviceName == null ? "\u2014" : deviceName);
    }

    public void updateDeviceTotalRecordsSent(int row) {
        TimedInt recordsSent = mainActivity.getTopicsSent(row);
        if (recordsSent.getTime() == -1L) {
            if (previousRecordsSent[row] != null && previousRecordsSent[row].getTime() == -1L) {
                return;
            }
            mRecordsSentLabels[row].setText(R.string.emptyText);
        } else {
            int timeSinceLastUpdate = (int)((System.currentTimeMillis() - recordsSent.getTime()) / 1000L);
            if (previousRecordsSent[row] != null && previousRecordsSent[row].equals(recordsSent) && previousRecordsSentTimer[row] == timeSinceLastUpdate) {
                return;
            }
            // Small test for Firebase Remote config.
            String message;
            if (radarConfiguration.getBoolean(CONDENSED_DISPLAY_KEY, true)) {
                message = String.format(Locale.US, "%1$4dk (%2$d)",
                        recordsSent.getValue() / 1000, timeSinceLastUpdate);
            } else {
                message = String.format(Locale.US, "%1$4d (updated %2$d sec. ago)",
                        recordsSent.getValue(), timeSinceLastUpdate);
            }
            mRecordsSentLabels[row].setText(message);
            previousRecordsSentTimer[row] = timeSinceLastUpdate;
        }
        previousRecordsSent[row] = recordsSent;
    }

    private void setText(TextView[] labels, float[] previousValue, int row, float value, String suffix, DecimalFormat formatter) {
        if (value == previousValue[row] || (Float.isNaN(value) && Float.isNaN(previousValue[row]))) {
            return;
        }
        previousValue[row] = value;
        if (Float.isNaN(value)) {
            // Only overwrite default value if enabled.
            if (labels[row].isEnabled()) {
                // em dash
                labels[row].setText("\u2014");
            }
        } else {
            labels[row].setText(formatter.format(value) + " " + suffix);
        }
    }
}
