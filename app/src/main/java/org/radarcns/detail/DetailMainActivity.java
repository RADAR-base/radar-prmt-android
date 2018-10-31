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

import android.content.Intent;
import android.view.View;

import org.radarcns.android.IRadarBinder;
import org.radarcns.android.MainActivity;
import org.radarcns.android.MainActivityView;
import org.radarcns.passive.ppg.PhonePpgActivity;

public class DetailMainActivity extends MainActivity {
    @Override
    protected MainActivityView createView() {
        return new DetailMainActivityView(this);
    }

    public void logout(View view) {
        IRadarBinder radarService = getRadarService();
        if (radarService != null) {
            radarService.getAuthState().invalidate(this);
        }
        startLogin(false);
    }

    public void showInfo(View view) {
        startActivity(new Intent(this, DetailInfoActivity.class));
    }

    public void showSettings(View view) {
        startActivity(new Intent(this, DetailSettingsActivity.class));
    }

    public void startPpgFragment() {
        startActivity(new Intent(this, PhonePpgActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
        overridePendingTransition(0, 0);
        finish();
    }
}
