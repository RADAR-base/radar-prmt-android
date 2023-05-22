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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat.CATEGORY_ALARM
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.radarbase.android.RadarApplication.Companion.radarApp
import org.radarbase.android.util.Boast
import org.radarbase.android.util.NotificationHandler.Companion.NOTIFICATION_CHANNEL_ALERT

/**
 * Starts MainActivity on boot if configured to do so
 */
class MainActivityBootStarter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if ((context.applicationContext as RadarApplicationImpl).isInForeground) {
            return
        }
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                context.startApp()
                Boast.makeText(context, R.string.appUpdated, Toast.LENGTH_LONG).show()
            }
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                context.startApp()
            }
        }
    }

    private fun Context.startApp() {
        if (!Settings.canDrawOverlays(this)) {
            radarApp.notificationHandler.notify(
                id = BOOT_START_NOTIFICATION_ID,
                channel = NOTIFICATION_CHANNEL_ALERT,
                includeStartIntent = true,
            ) {
                setContentTitle(getString(R.string.bootstart_title))
                setContentTitle(getString(R.string.bootstart_text))
                setCategory(CATEGORY_ALARM)
                setAutoCancel(true)
                setOngoing(true)
            }
        } else {
            packageManager.getLaunchIntentForPackage(packageName)?.also {
                startActivity(it)
            } ?: FirebaseCrashlytics.getInstance()
                .recordException(IllegalStateException("Cannot start RADAR app $packageName without launch intent"))
        }
    }

    companion object {
        const val BOOT_START_NOTIFICATION_ID = 35002
    }
}
