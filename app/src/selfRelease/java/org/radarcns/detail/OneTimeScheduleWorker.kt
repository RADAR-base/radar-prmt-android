package org.radarcns.detail

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.radarcns.detail.UpdateScheduledService.Companion.UPDATE_VERSION_NAME_KEY
import org.radarcns.detail.UpdateScheduledService.Companion.UPDATE_VERSION_URL_KEY
import kotlin.random.Random

class OneTimeScheduleWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val url =  inputData.getString(UPDATE_VERSION_URL_KEY)
        val versionName =  inputData.getString(UPDATE_VERSION_NAME_KEY)

        val intent = Intent(context, UpdatesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(FROM_NOTIFICATION_KEY, true)
            putExtra(UPDATE_VERSION_NAME_KEY, versionName)
            putExtra(UPDATE_VERSION_URL_KEY, url)
        }

        val uniqueInt = (System.currentTimeMillis() and 0xfffffff).toInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            uniqueInt,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )

        val title = context.getString(R.string.app_name)
        val content = context.getString(R.string.update_notification_content)

        val builder = NotificationCompat.Builder(context, UPDATE_NOTIFICATION_ID)
            .setSmallIcon(R.drawable.ic_baseline_update_24)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)

        createNotificationChannel()

        with(NotificationManagerCompat.from(context)) {
            notify(Random.nextInt(), builder.build())
        }

        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val descriptionText = context.getString(R.string.update_notification_content)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                UPDATE_NOTIFICATION_ID,
                UPDATE_NOTIFICATION_CHANNEL_NAME,
                importance
            ).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val FROM_NOTIFICATION_KEY = "from_notification"
        const val UPDATE_NOTIFICATION_ID = "com.radarcns.detail.update"
        const val UPDATE_NOTIFICATION_CHANNEL_NAME = "update_notification"
    }

}