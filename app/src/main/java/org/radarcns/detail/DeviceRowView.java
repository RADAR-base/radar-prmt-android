/*
 * Copyright 2017 The Hyve
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarcns.detail;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import org.radarcns.android.MainActivity;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceServiceConnection;
import org.radarcns.android.device.DeviceServiceProvider;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.android.util.Boast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Displays a single device row.
 */
public class DeviceRowView {
    private static final Logger logger = LoggerFactory.getLogger(DeviceRowView.class);
    private static final int MAX_UI_DEVICE_NAME_LENGTH = 25;

    private final static Map<DeviceStatusListener.Status, Integer> deviceStatusIconMap;
    private final static int deviceStatusIconDefault = R.drawable.status_searching;

    static {
        deviceStatusIconMap = new EnumMap<>(DeviceStatusListener.Status.class);
        deviceStatusIconMap.put(DeviceStatusListener.Status.CONNECTED, R.drawable.status_connected);
        deviceStatusIconMap.put(DeviceStatusListener.Status.DISCONNECTED, R.drawable.status_disconnected);
        deviceStatusIconMap.put(DeviceStatusListener.Status.READY, R.drawable.status_searching);
        deviceStatusIconMap.put(DeviceStatusListener.Status.CONNECTING, R.drawable.status_searching);
    }

    private final MainActivity mainActivity;

    private final DeviceServiceConnection connection;
    private final View mStatusIcon;
    private final ImageView mBatteryLabel;
    private final TextView mDeviceNameLabel;
    private final SharedPreferences devicePreferences;
    private String filter;
    private BaseDeviceState state;
    private String deviceName;
    private float previousBatteryLevel = Float.NaN;
    private String previousName;
    private DeviceStatusListener.Status previousStatus = null;

    DeviceRowView(MainActivity mainActivity, DeviceServiceProvider provider, ViewGroup root) {
        this.mainActivity = mainActivity;
        this.connection = provider.getConnection();
        devicePreferences = this.mainActivity.getSharedPreferences("device." + connection.getServiceClassName(), Context.MODE_PRIVATE);
        logger.info("Creating device row for provider {} and connection {}", provider, connection);
        LayoutInflater inflater = (LayoutInflater) this.mainActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.activity_overview_device_row, root);
        TableRow row = (TableRow) root.getChildAt(root.getChildCount() - 1);

        mStatusIcon = row.findViewById(R.id.status_icon);
        mDeviceNameLabel = row.findViewById(R.id.deviceName_label);
        mBatteryLabel = row.findViewById(R.id.battery_label);
        Button mDeviceInput = row.findViewById(R.id.inputDeviceButton);

        if (provider.isFilterable()) {
            mDeviceInput.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialogDeviceName();
                }
            });
//            mDeviceInput.setVisibility(View.VISIBLE);
            mDeviceInput.setEnabled(true);
        }

        mDeviceInput.setText(provider.getDisplayName());

        filter = "";
        setFilter(devicePreferences.getString("filter", ""));
        row.findViewById(R.id.refreshButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reconnectDevice();
            }
        });
    }

    private void dialogDeviceName() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this.mainActivity);
        builder.setTitle(this.mainActivity.getString(R.string.filter_title));

        // Layout containing label and input
        final LinearLayout layout = new LinearLayout(this.mainActivity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(70,0,70,0);

        // Label
        TextView label = new TextView(this.mainActivity);
        label.setText(R.string.filter_help_label);
        layout.addView(label);

        // Set up the input
        final EditText input = new EditText(this.mainActivity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(input);
        builder.setView(layout);

        // Set up the buttons
        input.setText(filter);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setFilter(input.getText().toString().trim());
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

    private void setFilter(String newValue) {
        if (filter.equals(newValue)) {
            logger.info("device filter did not change - ignoring");
            return;
        }
        // Set new value and process
        filter = newValue;
        devicePreferences.edit().putString("filter", filter).apply();

        String splitRegex = this.mainActivity.getString(R.string.filter_split_regex);
        Set<String> allowed = new HashSet<>();
        for (String s : filter.split(splitRegex)) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                allowed.add(trimmed);
            }
        }

        logger.info("setting device filter {}", allowed);

        this.mainActivity.getRadarService().setAllowedDeviceIds(connection, allowed);
    }

    private void reconnectDevice() {
        try {
            // will restart scanning after disconnect
            if (connection.isRecording()) {
                connection.stopRecording();
            }
        } catch (IndexOutOfBoundsException iobe) {
            Boast.makeText(this.mainActivity, "Could not restart scanning, there is no valid row index associated with this button.", Toast.LENGTH_LONG).show();
            logger.warn(iobe.getMessage());
        }
    }

    void update() {
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
        if (deviceName != null) {
            deviceName = deviceName.replace("Empatica", "").trim();
        }
    }

    void display() {
        updateBattery();
        updateDeviceName();
        updateDeviceStatus();
    }

    private void updateDeviceStatus() {
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

    private void updateBattery() {
        // Battery levels observed for E4 are 0.01, 0.1, 0.45 or 1
        float batteryLevel = state == null ? Float.NaN : state.getBatteryLevel();
        if (Objects.equals(previousBatteryLevel, batteryLevel)) {
            return;
        }
        previousBatteryLevel = batteryLevel;
        if (Float.isNaN(batteryLevel)) {
            mBatteryLabel.setImageResource(R.drawable.ic_battery_unknown);
        } else if (batteryLevel < 0.1) {
            mBatteryLabel.setImageResource(R.drawable.ic_battery_empty);
        } else if (batteryLevel < 0.3) {
            mBatteryLabel.setImageResource(R.drawable.ic_battery_low);
        } else if (batteryLevel < 0.6) {
            mBatteryLabel.setImageResource(R.drawable.ic_battery_50);
        } else if (batteryLevel < 0.85) {
            mBatteryLabel.setImageResource(R.drawable.ic_battery_80);
        } else {
            mBatteryLabel.setImageResource(R.drawable.ic_battery_full);
        }
    }

    private void updateDeviceName() {
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
}
