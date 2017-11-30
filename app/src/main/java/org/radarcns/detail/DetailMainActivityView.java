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

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.radarcns.android.MainActivityView;
import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.device.DeviceServiceProvider;
import org.radarcns.data.TimedInt;
import org.radarcns.phone.PhoneBluetoothProvider;
import org.radarcns.phone.PhoneContactListProvider;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.radarcns.android.RadarConfiguration.CONDENSED_DISPLAY_KEY;

public class DetailMainActivityView implements Runnable, MainActivityView {
    private static final DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private final DetailMainActivity mainActivity;
    private final List<DeviceRowView> rows = new ArrayList<>();
    private List<DeviceServiceProvider> savedConnections;

    private long previousTimestamp;
    private volatile String newServerStatus;

    // View elements
    private TextView mServerMessage;
    private TextView mUserId;
    private String userId;
    private String previousUserId;
    private TextView mProjectId;
    private String projectId;
    private String previousProjectId;

    DetailMainActivityView(DetailMainActivity activity) {
        this.mainActivity = activity;
        this.previousUserId = "";

        initializeViews();

        createRows();
    }

    private void createRows() {
        if (mainActivity.getRadarService() != null
                && !mainActivity.getRadarService().getConnections().equals(savedConnections)) {
            ViewGroup root = (ViewGroup) mainActivity.findViewById(R.id.deviceTable);
            while (root.getChildCount() > 1) {
                root.removeView(root.getChildAt(1));
            }
            rows.clear();
            boolean condensed = RadarConfiguration.getInstance().getBoolean(CONDENSED_DISPLAY_KEY, true);
            for (DeviceServiceProvider provider : mainActivity.getRadarService().getConnections()) {
                if (isDisplayable(provider)) {
                    rows.add(new DeviceRowView(mainActivity, provider, root, condensed));
                }
            }
            savedConnections = mainActivity.getRadarService().getConnections();
        }
    }

    private boolean isDisplayable(DeviceServiceProvider provider) {
        return  !(provider instanceof PhoneContactListProvider)  // TODO: fix PhoneContactListProvider.isDisplayable
                && !(provider instanceof PhoneBluetoothProvider) // TODO: fix PhoneBluetoothProvider.isDisplayable
                && provider.isDisplayable();
    }

    public void update() {
        createRows();

        userId = mainActivity.getUserId();
        projectId = mainActivity.getProjectId();
        for (DeviceRowView row : rows) {
            row.update();
        }
        if (mainActivity.getRadarService() != null) {
            newServerStatus = getServerStatusMessage();
        }
        mainActivity.runOnUiThread(this);
    }

    private String getServerStatusMessage() {
        TimedInt numberOfRecords = mainActivity.getRadarService().getLatestNumberOfRecordsSent();

        String message = null;
        if (numberOfRecords != null && numberOfRecords.getTime() >= 0 && previousTimestamp != numberOfRecords.getTime()) {
            previousTimestamp = numberOfRecords.getTime();

            String messageTimeStamp = timeFormat.format(numberOfRecords.getTime());

            if (numberOfRecords.getValue() < 0) {
                message = String.format(Locale.US, "last upload failed at %1$s", messageTimeStamp);
            } else {
                message = String.format(Locale.US, "last upload at %1$s", messageTimeStamp);
            }
        }
        return message;
    }

    private void initializeViews() {
        mainActivity.setContentView(R.layout.compact_overview);

        mServerMessage = (TextView) mainActivity.findViewById(R.id.statusServerMessage);

        mUserId = (TextView) mainActivity.findViewById(R.id.inputUserId);
        mProjectId = (TextView) mainActivity.findViewById(R.id.inputProjectId);
    }

    @Override
    public void run() {
        for (DeviceRowView row : rows) {
            row.display();
        }
        updateServerStatus();
        setUserId();
    }

    private void updateServerStatus() {
        String message = newServerStatus;

        if (message != null) {
            mServerMessage.setText(message);
        }
    }

    private void setUserId() {
        if (!Objects.equals(userId, previousUserId)) {
            if (userId == null) {
                mUserId.setVisibility(View.GONE);
            } else {
                if (previousUserId == null) {
                    mUserId.setVisibility(View.VISIBLE);
                }
                mUserId.setText(mainActivity.getString(R.string.user_id_message, userId));
            }
            previousUserId = userId;
        }
        if (!Objects.equals(projectId, previousProjectId)) {
            if (projectId == null) {
                mProjectId.setVisibility(View.GONE);
            } else {
                if (previousProjectId == null) {
                    mProjectId.setVisibility(View.VISIBLE);
                }
                mProjectId.setText(mainActivity.getString(R.string.study_id_message, projectId));
            }
            previousProjectId = projectId;
        }
    }
}
