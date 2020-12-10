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
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.radarbase.android.AbstractRadarApplication
import org.radarbase.android.RadarApplication
import org.radarbase.android.RadarConfiguration
import org.radarbase.android.config.AppConfigRadarConfiguration
import org.radarbase.android.config.FirebaseRemoteConfiguration
import org.radarbase.android.config.RemoteConfig
import org.slf4j.LoggerFactory
import org.slf4j.impl.HandroidLoggerAdapter
import kotlin.system.exitProcess

/**
 * Radar application class for the detailed application.
 */
class RadarApplicationImpl : AbstractRadarApplication(), LifecycleObserver {
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun enableCrashProcessing() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LoggerFactory.getLogger(RadarApplication::class.java).error("Uncaught error", throwable)

            val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                putExtra("crash", true)
            }
            if (intent == null) {
                FirebaseCrashlytics.getInstance()
                        .recordException(IllegalStateException("Cannot find launch intent for app"))
                return@setDefaultUncaughtExceptionHandler
            }

            val pendingIntent = PendingIntent.getActivity(baseContext, 0, intent, PendingIntent.FLAG_ONE_SHOT)
            (baseContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                    .set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 100, pendingIntent)

            defaultHandler?.uncaughtException(thread, throwable)
            exitProcess(2)
        }
    }

    override fun setupLogging() {
        HandroidLoggerAdapter.APP_NAME = "pRMT"
        HandroidLoggerAdapter.DEBUG = BuildConfig.DEBUG
        if (FirebaseCrashlytics.getInstance().didCrashOnPreviousExecution()) {
            Log.e("pRMT", "Crashed on previous boot")
        }
        HandroidLoggerAdapter.enableLoggingToFirebaseCrashlytics()
    }

    @Volatile
    var isInForeground: Boolean = false
        private set

    override val largeIcon: Bitmap
        get() = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

    override val smallIcon = R.drawable.ic_bt_connected

    override fun createRemoteConfiguration(): List<RemoteConfig> = listOf(
            FirebaseRemoteConfiguration(this, BuildConfig.DEBUG, R.xml.remote_config_defaults),
            AppConfigRadarConfiguration(this)
    )


    override fun createConfiguration(): RadarConfiguration {
        FirebaseAnalytics.getInstance(this).apply {
            setUserProperty(TEST_PHASE, if (BuildConfig.DEBUG) "dev" else "production")
        }
        return super.createConfiguration().apply {
            fetch()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        isInForeground = false
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        isInForeground = true
    }

    override val mainActivity: Class<MainActivityImpl> = MainActivityImpl::class.java

    override val loginActivity: Class<LoginActivityImpl> = LoginActivityImpl::class.java

    override val authService: Class<AuthServiceImpl> = AuthServiceImpl::class.java

    override val radarService: Class<RadarServiceImpl> = RadarServiceImpl::class.java

    companion object {
        private const val TEST_PHASE = "test_phase"
    }
}
