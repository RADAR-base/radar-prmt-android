package org.radarcns.detail

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import org.radarbase.android.RadarApplication.Companion.radarApp
import org.radarbase.producer.rest.RestClient
import org.radarcns.detail.UpdatesActivity.Companion.UPDATE_RELEASES_URL_KEY
import org.radarcns.detail.UpdatesActivity.Companion.UPDATE_VERSION_NAME_KEY
import org.radarcns.detail.UpdatesActivity.Companion.UPDATE_VERSION_URL_KEY
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class UpdateAlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val notificationHandler = context.radarApp.notificationHandler
        val url: String = intent?.extras?.getString(UPDATE_RELEASES_URL_KEY) ?: return
        val request = okhttp3.Request.Builder()
            .url(url)
            .build()
        val client: OkHttpClient = RestClient.global()
            .httpClientBuilder()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                logger.error("Failed to get latest release.", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = response.body?.string() ?: throw IOException("Missing response body")
                    if (!response.isSuccessful) throw IOException("Unexpected code ${response.code}: $responseBody")
                    val updatePackage = PackageUtil(context).getUpdatePackage(responseBody) ?: return@use

                    if (didCreateChannel.compareAndSet(false, true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        with(notificationHandler) {
                            manager?.createNotificationChannel(
                                id = UPDATE_NOTIFICATION_CHANNEL_ID,
                                importance = NotificationManager.IMPORTANCE_HIGH,
                                name = R.string.updates,
                                description = R.string.update_notification_content,
                            )
                        }
                    }

                    val updateVersionUrl = updatePackage.getString(UPDATE_VERSION_URL_KEY)
                    val updateVersionName = updatePackage.getString(UPDATE_VERSION_NAME_KEY)

                    val repeaterIntent = Intent(context, UpdatesActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(FROM_NOTIFICATION_KEY, true)
                        putExtra(UPDATE_VERSION_NAME_KEY, updateVersionName)
                        putExtra(UPDATE_VERSION_URL_KEY, updateVersionUrl)
                    }

                    notificationHandler.notify(
                        id = UPDATE_NOTIFICATION_INTENT_REQ_CODE,
                        channel = UPDATE_NOTIFICATION_CHANNEL_ID,
                        includeStartIntent = true,
                    ) {
                        setSmallIcon(R.drawable.ic_baseline_update_24)
                        setContentTitle(context.getString(R.string.app_name))
                        setContentText(context.getString(R.string.update_notification_content))
                        setContentIntent(PendingIntent.getActivity(
                            context,
                            UPDATE_NOTIFICATION_INTENT_REQ_CODE,
                            repeaterIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT
                        ))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                            setPriority(NotificationCompat.PRIORITY_MAX)
                        }
                    }
                }
            }
        })
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LoginActivityImpl::class.java)
        private val didCreateChannel = AtomicBoolean(false)

        const val FROM_NOTIFICATION_KEY = "from_notification"
        const val UPDATE_NOTIFICATION_CHANNEL_ID = "org.radarcns.detail.update"
        const val UPDATE_NOTIFICATION_CHANNEL_NAME = "update_notification"
        const val UPDATE_NOTIFICATION_INTENT_REQ_CODE = 200
    }
}
