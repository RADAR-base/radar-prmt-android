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

package org.radarbase.android

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.annotation.CallSuper
//import com.crashlytics.android.Crashlytics
//import io.fabric.sdk.android.Fabric
import org.radarbase.android.source.SourceService
import org.radarbase.android.util.NotificationHandler
//import org.slf4j.impl.HandroidLoggerAdapter

/** Provides the name and some metadata of the main activity  */
abstract class RadarApplication : Application() {
    private lateinit var innerNotificationHandler: NotificationHandler

    val notificationHandler: NotificationHandler
        get() = innerNotificationHandler.apply { onCreate() }

    lateinit var configuration: RadarConfiguration
        private set

    /** Large icon bitmap.  */
    abstract val largeIcon: Bitmap
    /** Small icon drawable resource ID.  */
    abstract val smallIcon: Int

    abstract val mainActivity: Class<out Activity>

    abstract val loginActivity: Class<out Activity>

    abstract val authService: Class<out Service>

    open val radarService: Class<out Service>
        get() = RadarService::class.java

    open fun configureProvider(bundle: Bundle) {}
    open fun onSourceServiceInvocation(service: SourceService<*>, bundle: Bundle, isNew: Boolean) {}
    open fun onSourceServiceDestroy(service: SourceService<*>) {}

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        setupLogging()
        configuration = createConfiguration()
        innerNotificationHandler = NotificationHandler(this)
    }

    protected open fun setupLogging() {
        // initialize crashlytics
        //Fabric.with(this, Crashlytics())
//        HandroidLoggerAdapter.DEBUG = BuildConfig.DEBUG
//        HandroidLoggerAdapter.APP_NAME = packageName
//        HandroidLoggerAdapter.enableLoggingToCrashlytics()
    }

    /**
     * Create a RadarConfiguration object. At implementation, the Firebase version needs to be set
     * for this as well as the defaults.
     *
     * @return configured RadarConfiguration
     */
    protected abstract fun createConfiguration(): RadarConfiguration

    companion object {
        val Context.radarApp: RadarApplication
            get() = applicationContext as RadarApplication

        val Context.radarConfig: RadarConfiguration
            get() = radarApp.configuration
    }
}
