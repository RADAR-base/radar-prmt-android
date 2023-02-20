package org.radarcns.detail

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.radarbase.android.RadarApplication.Companion.radarApp
import org.radarbase.android.util.toPendingIntentFlag
import org.radarcns.detail.UpdatesActivity.Companion.UPDATE_RELEASES_URL_KEY
import org.radarcns.detail.UpdatesActivity.Companion.UPDATE_VERSION_NAME_KEY
import org.radarcns.detail.UpdatesActivity.Companion.UPDATE_VERSION_URL_KEY
import java.util.concurrent.atomic.AtomicBoolean

class UpdateAlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val notificationHandler = context.radarApp.notificationHandler
        val url: String = intent?.extras?.getString(UPDATE_RELEASES_URL_KEY) ?: return

        val githubClient = GithubAssetClient()
        githubClient.retrieveLatestAsset(url) { asset ->
            if (!PackageUtil(context).assetIsUpdate(asset)) {
                return@retrieveLatestAsset
            }

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

            val repeaterIntent = Intent(context, UpdatesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(FROM_NOTIFICATION_KEY, true)
                putExtra(UPDATE_VERSION_NAME_KEY, asset.tag)
                putExtra(UPDATE_VERSION_URL_KEY, asset.url)
            }

            notificationHandler.notify(
                id = UPDATE_NOTIFICATION_INTENT_REQ_CODE,
                channel = UPDATE_NOTIFICATION_CHANNEL_ID,
                includeStartIntent = true,
            ) {
                setSmallIcon(R.drawable.baseline_update_black_24dp)
                setContentTitle(context.getString(R.string.app_name))
                setContentText(context.getString(R.string.update_notification_content))
                setContentIntent(PendingIntent.getActivity(
                    context,
                    UPDATE_NOTIFICATION_INTENT_REQ_CODE,
                    repeaterIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT.toPendingIntentFlag()
                ))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    setPriority(Notification.PRIORITY_MAX)
                }
            }
        }
    }

    companion object {
        private val didCreateChannel = AtomicBoolean(false)

        const val FROM_NOTIFICATION_KEY = "from_notification"
        const val UPDATE_NOTIFICATION_CHANNEL_ID = "org.radarcns.detail.update"
        const val UPDATE_NOTIFICATION_INTENT_REQ_CODE = 200
    }
}
