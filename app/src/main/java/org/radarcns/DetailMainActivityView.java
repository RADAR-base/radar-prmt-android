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

package org.radarcns;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.radarcns.android.MainActivity;
import org.radarcns.android.MainActivityView;
import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.device.DeviceServiceConnection;
import org.radarcns.android.device.DeviceServiceProvider;
import org.radarcns.android.kafka.ServerStatusListener;
import org.radarcns.android.util.Boast;
import org.radarcns.data.TimedInt;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.radarcns.android.RadarConfiguration.CONDENSED_DISPLAY_KEY;
import static org.radarcns.android.RadarConfiguration.DEFAULT_GROUP_ID_KEY;

public class DetailMainActivityView implements Runnable, MainActivityView {
    private static final DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private final MainActivity mainActivity;
    private final RadarConfiguration radarConfiguration;
    private final List<DeviceRowView> rows;

    private final static Map<ServerStatusListener.Status, Integer> serverStatusIconMap;
    static {
        serverStatusIconMap = new EnumMap<>(ServerStatusListener.Status.class);
        serverStatusIconMap.put(ServerStatusListener.Status.CONNECTED, org.radarcns.R.drawable.status_connected);
        serverStatusIconMap.put(ServerStatusListener.Status.DISCONNECTED, org.radarcns.R.drawable.status_disconnected);
        serverStatusIconMap.put(ServerStatusListener.Status.DISABLED, org.radarcns.R.drawable.status_disconnected);
        serverStatusIconMap.put(ServerStatusListener.Status.READY, org.radarcns.R.drawable.status_searching);
        serverStatusIconMap.put(ServerStatusListener.Status.CONNECTING, org.radarcns.R.drawable.status_searching);
        serverStatusIconMap.put(ServerStatusListener.Status.UPLOADING, org.radarcns.R.drawable.status_uploading);
        serverStatusIconMap.put(ServerStatusListener.Status.UPLOADING_FAILED, org.radarcns.R.drawable.status_error);
    }
    private final static int serverStatusIconDefault = org.radarcns.R.drawable.status_disconnected;

    private final SharedPreferences preferences;

    private String previousTopic;
    private TimedInt previousNumberOfTopicsSent;
    private ServerStatusListener.Status previousServerStatus;
    private String newServerStatus;
    private String userId;
    private RadarConfiguration.FirebaseStatus previousFirebaseStatus;

    // View elements
    private View mServerStatusIcon;
    private TextView mServerMessage;
    private Button mGroupIdInputButton;
    private View mFirebaseStatusIcon;
    private TextView mFirebaseMessage;

    DetailMainActivityView(MainActivity activity, RadarConfiguration radarConfiguration) {
        this.radarConfiguration = radarConfiguration;
        this.mainActivity = activity;

        initializeViews();

        rows = new ArrayList<>();
        ViewGroup root = (ViewGroup) activity.findViewById(org.radarcns.R.id.deviceTable);
        boolean condensed = radarConfiguration.getBoolean(CONDENSED_DISPLAY_KEY, true);
        for (DeviceServiceProvider provider : activity.getConnections()) {
            if (provider.isDisplayable()) {
                rows.add(new DeviceRowView(mainActivity, provider, root, condensed));
            }
        }

        preferences = mainActivity.getSharedPreferences("main", Context.MODE_PRIVATE);
        setUserId(preferences.getString("userId", ""));
    }

    public void update() throws RemoteException {
        for (DeviceRowView row : rows) {
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
        mainActivity.setContentView(org.radarcns.R.layout.activity_overview);

        mServerStatusIcon = mainActivity.findViewById(org.radarcns.R.id.statusServer);
        mServerMessage = (TextView) mainActivity.findViewById( org.radarcns.R.id.statusServerMessage);
        mGroupIdInputButton = (Button) mainActivity.findViewById(org.radarcns.R.id.inputGroupId);
        mGroupIdInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialogInputGroupId();
            }
        });
        mGroupIdInputButton.setText(userId);
        mFirebaseStatusIcon = mainActivity.findViewById(org.radarcns.R.id.firebaseStatus);
        mFirebaseMessage = (TextView) mainActivity.findViewById( org.radarcns.R.id.firebaseStatusMessage);
    }

    @Override
    public void run() {
        for (DeviceRowView row : rows) {
            row.display();
        }
        updateServerStatus();
        updateFirebaseStatus();
    }

    private void updateFirebaseStatus() {
        RadarConfiguration.FirebaseStatus status = radarConfiguration.getStatus();
        if (status == previousFirebaseStatus) {
            return;
        }
        previousFirebaseStatus = status;

        switch (status) {
            case FETCHED:
                mFirebaseStatusIcon.setBackgroundResource(org.radarcns.R.drawable.status_connected);
                mFirebaseMessage.setText("Remote config fetched from the server ("
                        + timeFormat.format( System.currentTimeMillis() ) + ")");
                break;
            case UNAVAILABLE:
                mFirebaseStatusIcon.setBackgroundResource(org.radarcns.R.drawable.status_disconnected);
                mFirebaseMessage.setText(org.radarcns.R.string.playServicesUnavailable);
                break;
            case FETCHING:
                mFirebaseMessage.setText(org.radarcns.R.string.firebase_fetching);
                mFirebaseStatusIcon.setBackgroundResource(org.radarcns.R.drawable.status_searching);
                break;
            case ERROR:
                mFirebaseStatusIcon.setBackgroundResource(org.radarcns.R.drawable.status_error);
                mFirebaseMessage.setText("Failed to fetch remote config ("
                        + timeFormat.format( System.currentTimeMillis() ) + ")");
                break;
            default:
                // no action
        }
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
        builder.setPositiveButton(org.radarcns.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setUserId(input.getText().toString().trim());
            }
        });
        builder.setNegativeButton(org.radarcns.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void setUserId(String newValue) {
        if (!newValue.isEmpty()) {
            userId = newValue;
        } else {
            userId = radarConfiguration.getString(DEFAULT_GROUP_ID_KEY);
        }
        preferences.edit().putString("userId", userId).apply();
        mGroupIdInputButton.setText(userId);

        // Set group/user id for each active connection
        try {
            for (DeviceServiceProvider provider : mainActivity.getConnections()) {
                DeviceServiceConnection connection = provider.getConnection();
                if (connection.hasService()) {
                    connection.setUserId(userId);
                }
            }
        } catch (RemoteException re) {
            Boast.makeText(mainActivity, "Could not set the patient id", Toast.LENGTH_LONG).show();
        }
    }

}
