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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.radarcns.android.RadarApplication;
import org.radarcns.android.RadarConfiguration;

/**
 * Radar application class for the detailed application.
 */
public class DetailRadarApplication extends RadarApplication {
    @Override
    public Bitmap getLargeIcon() {
        return BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
    }

    @Override
    public int getSmallIcon() {
        return org.radarcns.android.R.drawable.ic_bt_connected;
    }

    @Override
    protected RadarConfiguration createConfiguration() {
        // TODO: turn off developer mode
        return RadarConfiguration.configure(this, true, R.xml.remote_config_defaults);
    }
}
