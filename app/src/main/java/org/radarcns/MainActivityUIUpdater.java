package org.radarcns;

import android.content.Context;
import android.content.DialogInterface;
import android.os.RemoteException;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.radarcns.android.BaseDeviceState;
import org.radarcns.android.DeviceServiceConnection;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.RadarServiceProvider;
import org.radarcns.data.TimedInt;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.util.Boast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.radarcns.RadarConfiguration.CONDENSED_DISPLAY_KEY;
import static org.radarcns.RadarConfiguration.DEFAULT_GROUP_ID_KEY;

public class MainActivityUIUpdater implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MainActivityUIUpdater.class);
    private static final int MAX_UI_DEVICE_NAME_LENGTH = 25;
    private static final DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    /**
     * Data formats
     */
    private final DecimalFormat singleDecimal = new DecimalFormat("0.0");
    private final DecimalFormat doubleDecimal = new DecimalFormat("0.00");
    private final DecimalFormat noDecimals = new DecimalFormat("0");
    private final MainActivity mainActivity;
    private final RadarConfiguration radarConfiguration;
    private final List<DeviceRow> rows;

    private View mServerStatusIcon;
    private TextView mServerMessage;

    private final Map<ServerStatusListener.Status, Integer> serverStatusIconMap;
    private final static int serverStatusIconDefault = R.drawable.status_disconnected;

    private final Map<DeviceStatusListener.Status, Integer> deviceStatusIconMap;
    private final static int deviceStatusIconDefault = R.drawable.status_searching;

    private String previousTopic;
    private TimedInt previousNumberOfTopicsSent;
    private ServerStatusListener.Status previousServerStatus;
    private String newServerStatus;
    private String userId;
    private Button mGroupIdInputButton;

    MainActivityUIUpdater(MainActivity activity, RadarConfiguration radarConfiguration) {
        this.radarConfiguration = radarConfiguration;
        this.mainActivity = activity;

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

        userId = radarConfiguration.getString(DEFAULT_GROUP_ID_KEY);

        initializeViews();

        rows = new ArrayList<>();
        ViewGroup root = (ViewGroup) activity.findViewById(R.id.deviceTable);
        for (RadarServiceProvider provider : activity.getConnections()) {
            if (provider.isDisplayable()) {
                rows.add(new DeviceRow(provider, root));
            }
        }
    }

    public void update() throws RemoteException {
        for (DeviceRow row : rows) {
            row.update();
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
        mServerStatusIcon = mainActivity.findViewById(R.id.statusServer);
        mServerMessage = (TextView) mainActivity.findViewById( R.id.statusServerMessage);
        mGroupIdInputButton = (Button) mainActivity.findViewById(R.id.inputGroupId);
        mGroupIdInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialogInputGroupId();
            }
        });
        mGroupIdInputButton.setText(userId);
    }

    @Override
    public void run() {
        for (DeviceRow row : rows) {
            row.display();
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


    public void dialogInputGroupId() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mainActivity);
        builder.setTitle("Patient Identifier:");

        // Set up the input
        final EditText input = new EditText(mainActivity);
        // Specify the type of input expected
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        input.setText(userId);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                userId = input.getText().toString();
                mGroupIdInputButton.setText(userId);

                // Set group/user id for each active connection
                try {
                    for (RadarServiceProvider provider : mainActivity.getConnections()) {
                        DeviceServiceConnection connection = provider.getConnection();
                        if (connection.hasService()) {
                            connection.setUserId(userId);
                        }
                    }
                } catch (RemoteException re) {
                    Boast.makeText(mainActivity, "Could not set the patient id", Toast.LENGTH_LONG).show();
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private class DeviceRow {
        private final DeviceServiceConnection connection;
        private final View mStatusIcon;
        private final TextView mTemperatureLabel;
        private final TextView mHeartRateLabel;
        private final TextView mAccelerationLabel;
        private final TextView mRecordsSentLabel;
        private final ImageView mBatteryLabel;
        private final TextView mDeviceNameLabel;
        private final Button mDeviceInputButton;
        private String mInputDeviceKey;
        private BaseDeviceState state;
        private String deviceName;
        private TimedInt previousRecordsSent;
        private float previousTemperature = Float.NaN;
        private float previousBatteryLevel = Float.NaN;
        private float previousHeartRate = Float.NaN;
        private float previousAcceleration = Float.NaN;
        private int previousRecordsSentTimer = -1;
        private String previousName;
        private DeviceStatusListener.Status previousStatus = null;

        private DeviceRow(RadarServiceProvider provider, ViewGroup root) {
            this.connection = provider.getConnection();
            LayoutInflater inflater = (LayoutInflater) mainActivity.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            RelativeLayout row = (RelativeLayout) inflater.inflate(R.layout.activity_overview_device_row, root);
            TextView deviceTypeLabel = (TextView) row.findViewById(R.id.deviceType);
            deviceTypeLabel.setText(provider.getDisplayName());

            mStatusIcon = row.findViewById(R.id.status_icon);
            mTemperatureLabel = (TextView) row.findViewById(R.id.temperature_label);
            mHeartRateLabel = (TextView) row.findViewById(R.id.heartRate_label);
            mAccelerationLabel = (TextView) row.findViewById(R.id.acceleration_label);
            mRecordsSentLabel = (TextView) row.findViewById(R.id.recordsSent_label);
            mDeviceNameLabel = (TextView) row.findViewById(R.id.deviceName_label);
            mBatteryLabel = (ImageView) row.findViewById(R.id.battery_label);
            mDeviceInputButton = (Button) row.findViewById(R.id.inputDeviceNameButton);
            mDeviceInputButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialogDeviceName();
                }
            });

            mInputDeviceKey = "";
            row.findViewById(R.id.refreshButton).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    reconnectDevice();
                }
            });
        }

        public void dialogDeviceName() {
            AlertDialog.Builder builder = new AlertDialog.Builder(mainActivity);
            builder.setTitle("Device Serial Number:");

            // Set up the input
            final EditText input = new EditText(mainActivity);
            // Specify the type of input expected
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            builder.setView(input);

            // Set up the buttons
            input.setText(mInputDeviceKey);
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Remember previous value
                    String oldValue = mInputDeviceKey;

                    // Set new value and process
                    mInputDeviceKey = input.getText().toString().trim();
                    if (!mInputDeviceKey.equals(oldValue)) {
                        return;
                    }
                    Set<String> allowed;
                    String splitRegex = mainActivity.getString(R.string.deviceKeySplitRegex);
                    allowed = new HashSet<>(Arrays.asList(mInputDeviceKey.split(splitRegex)));
                    Iterator<String> iter = allowed.iterator();
                    // remove empty strings
                    while (iter.hasNext()) {
                        if (iter.next().trim().isEmpty()) {
                            iter.remove();
                        }
                    }

                    mainActivity.setAllowedDeviceIds(connection, allowed);
                    mDeviceInputButton.setText(mInputDeviceKey);
                }
            });
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            builder.show();
        }

        public void reconnectDevice() {
            try {
                // will restart scanning after disconnect
                mainActivity.disconnect(connection);
            } catch (IndexOutOfBoundsException iobe) {
                Boast.makeText(mainActivity, "Could not restart scanning, there is no valid row index associated with this button.", Toast.LENGTH_LONG).show();
                logger.warn(iobe.getMessage());
            }
        }

        private void update() throws RemoteException {
            if (connection.hasService()) {
                state = connection.getDeviceData();
                switch (state.getStatus()) {
                    case CONNECTED:
                    case CONNECTING:
                        deviceName = connection.getDeviceName();
                        break;
                    default:
                        deviceName = null;
                        break;
                }
            } else {
                state = null;
                deviceName = null;
            }
        }

        public void display() {
            updateAcceleration();
            updateBattery();
            updateDeviceName();
            updateDeviceStatus();
            updateDeviceTotalRecordsSent();
            updateHeartRate();
            updateTemperature();
        }

        public void updateDeviceStatus() {
            // Connection status. Change icon used.
            DeviceStatusListener.Status status;
            if (state == null) {
                status = DeviceStatusListener.Status.DISCONNECTED;
            } else {
                status = state.getStatus();
            }
            if (!Objects.equals(status, previousStatus)) {
                logger.info("Device status is {}", status);
                previousStatus = status;
                Integer statusIcon = deviceStatusIconMap.get(status);
                int resource = statusIcon != null ? statusIcon : deviceStatusIconDefault;
                mStatusIcon.setBackgroundResource(resource);
            }
        }

        public void updateTemperature() {
            if (state != null && !state.hasTemperature()) {
                return;
            }
            // \u2103 == ℃
            float temperature = state == null ? Float.NaN : state.getTemperature();
            if (Objects.equals(previousTemperature, temperature)) {
                return;
            }
            previousTemperature = temperature;
            setText(mTemperatureLabel, temperature, "\u2103", singleDecimal);
        }

        public void updateHeartRate() {
            if (state != null && !state.hasHeartRate()) {
                return;
            }
            float heartRate = state == null ? Float.NaN : state.getHeartRate();
            if (Objects.equals(previousHeartRate, heartRate)) {
                return;
            }
            previousHeartRate = heartRate;
            setText(mHeartRateLabel, heartRate, "bpm", noDecimals);
        }

        public void updateAcceleration() {
            if (state != null && !state.hasAcceleration()) {
                return;
            }
            float acceleration = state == null ? Float.NaN : state.getAccelerationMagnitude();
            if (Objects.equals(previousAcceleration, acceleration)) {
                return;
            }
            previousAcceleration = acceleration;
            setText(mAccelerationLabel, acceleration, "g", doubleDecimal);
        }

        public void updateBattery() {
            // Battery levels observed for E4 are 0.01, 0.1, 0.45 or 1
            float batteryLevel = state == null ? Float.NaN : state.getBatteryLevel();
            if (Objects.equals(previousBatteryLevel, batteryLevel)) {
                return;
            }
            previousBatteryLevel = batteryLevel;
            if (Float.isNaN(batteryLevel)) {
                mBatteryLabel.setImageResource(R.drawable.ic_battery_unknown);
                // up to 100%
            } else if (batteryLevel > 0.5) {
                mBatteryLabel.setImageResource(R.drawable.ic_battery_full);
                // up to 45%
            } else if (batteryLevel > 0.2) {
                mBatteryLabel.setImageResource(R.drawable.ic_battery_50);
                // up to 10%
            } else if (batteryLevel > 0.1) {
                mBatteryLabel.setImageResource(R.drawable.ic_battery_low);
                // up to 5% [what are possible values below 10%?]
            } else {
                mBatteryLabel.setImageResource(R.drawable.ic_battery_empty);
            }
        }

        public void updateDeviceName() {
            if (Objects.equals(deviceName, previousName)) {
                return;
            }
            previousName = deviceName;
            // Restrict length of name that is shown.
            if (deviceName != null && deviceName.length() > MAX_UI_DEVICE_NAME_LENGTH - 3) {
                deviceName = deviceName.substring(0, MAX_UI_DEVICE_NAME_LENGTH) + "...";
            }

            // \u2014 == —
            mDeviceNameLabel.setText(deviceName == null ? "\u2014" : deviceName);
        }

        public void updateDeviceTotalRecordsSent() {
            TimedInt recordsSent = mainActivity.getTopicsSent(connection);
            if (recordsSent.getTime() == -1L) {
                if (previousRecordsSent != null && previousRecordsSent.getTime() == -1L) {
                    return;
                }
                mRecordsSentLabel.setText(R.string.emptyText);
            } else {
                int timeSinceLastUpdate = (int)((System.currentTimeMillis() - recordsSent.getTime()) / 1000L);
                if (previousRecordsSent != null && previousRecordsSent.equals(recordsSent) && previousRecordsSentTimer == timeSinceLastUpdate) {
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
                mRecordsSentLabel.setText(message);
                previousRecordsSentTimer = timeSinceLastUpdate;
            }
            previousRecordsSent = recordsSent;
        }

        private void setText(TextView label, float value, String suffix, DecimalFormat formatter) {
            if (Float.isNaN(value)) {
                // Only overwrite default value if enabled.
                if (label.isEnabled()) {
                    // em dash
                    label.setText("\u2014");
                }
            } else {
                label.setText(formatter.format(value) + " " + suffix);
            }
        }
    }
}
