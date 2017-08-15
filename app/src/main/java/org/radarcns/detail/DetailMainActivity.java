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

import org.radarcns.android.MainActivity;
import org.radarcns.android.MainActivityView;
import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.auth.LoginActivity;

import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.RECEIVE_BOOT_COMPLETED;

public class DetailMainActivity extends MainActivity {
    @Override
    protected RadarConfiguration createConfiguration() {
        // TODO: turn off developer mode
        return RadarConfiguration.configure(this, true, R.xml.remote_config_defaults);
    }

    @Override
    protected Class<? extends LoginActivity> loginActivity() {
        return RadarLoginActivity.class;
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
        getAuthState().invalidate(this);
        startLogin();
    }
}
