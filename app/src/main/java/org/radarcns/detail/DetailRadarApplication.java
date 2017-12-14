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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.radarcns.android.RadarApplication;
import org.radarcns.android.RadarConfiguration;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.HandroidLoggerAdapter;

/**
 * Radar application class for the detailed application.
 */
public class DetailRadarApplication extends RadarApplication {
    private static final String TEST_PHASE = "test_phase";

    @Override
    public void onCreate() {
        super.onCreate();

        final Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                LoggerFactory.getLogger(DetailRadarApplication.class).error("Uncaught error", throwable);
                Intent intent = new Intent(DetailRadarApplication.this, DetailRadarApplication.class);
                intent.putExtra("crash", true);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent pendingIntent = PendingIntent.getActivity(getBaseContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);
                AlarmManager mgr = (AlarmManager) getBaseContext().getSystemService(Context.ALARM_SERVICE);
                if (mgr != null) {
                    mgr.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 100, pendingIntent);
                }
                if (handler != null) {
                    handler.uncaughtException(thread, throwable);
                }
                System.exit(2);
            }
        });
    }

    @Override
    protected void setupLogging() {
        HandroidLoggerAdapter.APP_NAME = "pRMT";
        HandroidLoggerAdapter.DEBUG = BuildConfig.DEBUG;
    }

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
        RadarConfiguration config = RadarConfiguration.configure(this, true, R.xml.remote_config_defaults);
        FirebaseAnalytics firebase = FirebaseAnalytics.getInstance(this);
        firebase.setUserProperty(TEST_PHASE, "dev");
        config.fetch();
        return config;
    }
}
