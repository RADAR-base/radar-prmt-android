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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.*
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.radarbase.android.AbstractRadarApplication
import org.radarbase.android.RadarConfiguration
import org.radarbase.android.config.AppConfigRadarConfiguration
import org.radarbase.android.config.FirebaseRemoteConfiguration
import org.radarbase.android.config.RemoteConfig
import org.slf4j.impl.HandroidLoggerAdapter

/**
 * Radar application class for the detailed application.
 */
class RadarApplicationImpl : AbstractRadarApplication(), LifecycleEventObserver {
    var enableCrashRecovery: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun enableCrashProcessing() {
        enableCrashRecovery = true
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

    val largeIcon: Bitmap
        get() = AppCompatResources.getDrawable(this, R.mipmap.ic_launcher)!!.toBitmap()

    val smallIcon = R.drawable.ic_bt_connected

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

    override val mainActivity: Class<MainActivityImpl> = MainActivityImpl::class.java

    override val loginActivity: Class<LoginActivityImpl> = LoginActivityImpl::class.java

    override val authService: Class<AuthServiceImpl> = AuthServiceImpl::class.java

    override val radarService: Class<RadarServiceImpl> = RadarServiceImpl::class.java

    companion object {
        private const val TEST_PHASE = "test_phase"
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        isInForeground = when (event) {
            Lifecycle.Event.ON_STOP -> false
            Lifecycle.Event.ON_START -> true
            else -> isInForeground
        }
    }
}
