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

package org.radarcns.detail

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import com.crashlytics.android.Crashlytics
import com.google.firebase.analytics.FirebaseAnalytics
import org.radarbase.android.FirebaseRadarConfiguration
import org.radarbase.android.RadarApplication
import org.radarbase.android.RadarConfiguration
import org.slf4j.LoggerFactory
import org.slf4j.impl.HandroidLoggerAdapter

/**
 * Radar application class for the detailed application.
 */
class RadarApplicationImpl : RadarApplication() {
    fun enableCrashProcessing() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LoggerFactory.getLogger(RadarApplication::class.java).error("Uncaught error", throwable)

            val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                putExtra("crash", true)
            }
            if (intent == null) {
                Crashlytics.logException(IllegalStateException("Cannot find launch intent for app"))
                return@setDefaultUncaughtExceptionHandler
            }

            val pendingIntent = PendingIntent.getActivity(baseContext, 0, intent, PendingIntent.FLAG_ONE_SHOT)
            (baseContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                    .set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 100, pendingIntent)

            defaultHandler?.uncaughtException(thread, throwable)
            System.exit(2)
        }
    }

    override fun setupLogging() {
        HandroidLoggerAdapter.APP_NAME = "pRMT"
        HandroidLoggerAdapter.DEBUG = BuildConfig.DEBUG
    }

    override val largeIcon: Bitmap
            get() = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

    override val smallIcon: Int
            get() = R.drawable.ic_bt_connected

    override fun createConfiguration(): RadarConfiguration {
        FirebaseAnalytics.getInstance(this).apply {
            setUserProperty(TEST_PHASE, if (BuildConfig.DEBUG) "dev" else "production")
        }

        return FirebaseRadarConfiguration(this, true, R.xml.remote_config_defaults).apply {
            fetch()
        }
    }

    override val mainActivity: Class<MainActivityImpl>
            get() = MainActivityImpl::class.java

    override val loginActivity: Class<LoginActivityImpl>
            get() = LoginActivityImpl::class.java

    override val authService: Class<AuthServiceImpl>
            get() = AuthServiceImpl::class.java

    override val radarService: Class<RadarServiceImpl>
            get() = RadarServiceImpl::class.java

    companion object {
        private const val TEST_PHASE = "test_phase"
    }
}
