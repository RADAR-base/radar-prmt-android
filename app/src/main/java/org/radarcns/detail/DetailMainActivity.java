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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;

import org.radarcns.android.MainActivity;
import org.radarcns.android.MainActivityView;
import org.radarcns.android.RadarConfiguration;

import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.RECEIVE_BOOT_COMPLETED;
import static org.radarcns.detail.LoginActivity.EXTRA_USERNAME;

public class DetailMainActivity extends MainActivity {
    @Override
    protected RadarConfiguration createConfiguration() {
        // TODO: turn off developer mode
        RadarConfiguration configuration = RadarConfiguration.configure(this, true, R.xml.remote_config_defaults);
        SharedPreferences preferences = getSharedPreferences("main", Context.MODE_PRIVATE);
        String userId;
        if (getIntent() != null && getIntent().hasExtra(EXTRA_USERNAME)) {
            userId = getIntent().getStringExtra(EXTRA_USERNAME);
            preferences.edit().putString("userId", userId).apply();
        } else {
            userId = preferences.getString("userId", "");
        }
        if (!userId.isEmpty()) {
            configuration.put(RadarConfiguration.DEFAULT_GROUP_ID_KEY, userId);
        }
        return configuration;
    }

    @Override
    protected void onConfigChanged() {
        configureRunAtBoot(MainActivityBootStarter.class);
    }

    @Override
    protected MainActivityView createView() {
        return new DetailMainActivityView(this, getRadarConfiguration());
    }

    @Override
    protected List<String> getActivityPermissions() {
        List<String> superPermissions = super.getActivityPermissions();
        List<String> result = new ArrayList<>(superPermissions.size() + 1);
        result.addAll(superPermissions);
        result.add(RECEIVE_BOOT_COMPLETED);
        return result;
    }

    public void logout(View view) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(Intent.EXTRA_DATA_REMOVED, "username");
        startActivity(intent);
        finish();
    }
}
