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

import android.support.annotation.CallSuper;
import android.view.View;


import org.radarcns.android.*;

public class DetailMainActivity extends MainActivity {

    @Override
    protected Class<RadarLoginActivity> loginActivity() {
        return RadarLoginActivity.class;
    }

    @Override
    protected MainActivityView createView() {
        return new DetailMainActivityView(this);
    }

    @Override
    protected void onConfigChanged() {
        super.onConfigChanged();
        
    }


    public void logout(View view) {
        IRadarService radarService = getRadarService();
        if (radarService != null) {
            radarService.getAuthState().invalidate(this);
        }
        startLogin(false);
    }

    @Override
    protected Class<? extends RadarService> radarService() {
        return DetailRadarService.class;
    }

    public String getUserId() {
        return RadarConfiguration.getInstance().getString(RadarConfiguration.USER_ID_KEY, "");
    }
}
