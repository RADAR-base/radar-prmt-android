package org.radarbase.android.util

import android.app.Notification
import android.app.Notification.DEFAULT_SOUND
import android.app.Notification.DEFAULT_VIBRATE
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.annotation.RequiresApi
import org.radarbase.android.R
import org.radarbase.android.RadarApplication
import org.slf4j.LoggerFactory

/**
 * Handle notifications and notification channels.
 */
class NotificationHandler(private val context: Context) {
    private var isCreated: Boolean = false

    val manager : NotificationManager?
        get() = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager?

    fun onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannels()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createNotificationChannels() {
        manager?.run {
            synchronized(this) {
                if (isCreated) {
                    return
                }
                isCreated = true
            }
            logger.debug("Creating notification channels")
            createNotificationChannel(NOTIFICATION_CHANNEL_INFO,
                    NotificationManager.IMPORTANCE_LOW,
                    R.string.channel_info_name, R.string.channel_info_description)

            createNotificationChannel(NOTIFICATION_CHANNEL_NOTIFY,
                    NotificationManager.IMPORTANCE_DEFAULT,
                    R.string.channel_notify_name, R.string.channel_notify_description)

            createNotificationChannel(NOTIFICATION_CHANNEL_ALERT,
                    NotificationManager.IMPORTANCE_HIGH,
                    R.string.channel_alert_name, R.string.channel_alert_description)

            createNotificationChannel(NOTIFICATION_CHANNEL_FINAL_ALERT,
                    NotificationManager.IMPORTANCE_HIGH,
                    R.string.channel_final_alert_name,
                    R.string.channel_final_alert_description) {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder().apply {
                            setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        }.build())
            }
        }
    }

    /**
     * Creates a notification channel that is not yet added to the notification manager.
     * @param id channel ID
     * @param importance NotificationManager importance constant
     * @param name string resource ID of the human readable name
     * @param description string resource ID of the human readable description.
     * @return not yet added notification channel
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    fun NotificationManager.createNotificationChannel(id: String, importance: Int, name: Int,
                                                   description: Int,
                                                   update: (NotificationChannel.() -> Unit)? = null) {
        val channel = NotificationChannel(id, context.getString(name), importance).apply {
            setDescription(context.getString(description))
            update?.let { it() }
        }
        createNotificationChannel(channel)
    }

    fun create(channel: String, includeStartIntent: Boolean,
               buildNotification: Notification.Builder.() -> Unit): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channel)
        } else {
            deprecatedBuilder(channel)
        }
        return builder.apply {
            updateNotificationAppSettings(includeStartIntent)
            buildNotification()
        }.build()
    }

    fun notify(id: Int, channel: String, includeStartIntent: Boolean,
               buildNotification: Notification.Builder.() -> Unit): NotificationRegistration {
        manager?.notify(id, create(channel, includeStartIntent, buildNotification))
        return NotificationRegistration(manager, id)
    }

    @Suppress("DEPRECATION")
    private fun deprecatedBuilder(channel: String): Notification.Builder {
        return Notification.Builder(context).apply {
            when (channel) {
                NOTIFICATION_CHANNEL_INFO -> setPriority(Notification.PRIORITY_LOW)
                NOTIFICATION_CHANNEL_NOTIFY -> setPriority(Notification.PRIORITY_DEFAULT)
                NOTIFICATION_CHANNEL_ALERT -> {
                    setDefaults(DEFAULT_VIBRATE)
                    setPriority(Notification.PRIORITY_HIGH)
                }
                NOTIFICATION_CHANNEL_FINAL_ALERT -> {
                    setDefaults(DEFAULT_SOUND or DEFAULT_VIBRATE)
                    setPriority(Notification.PRIORITY_HIGH)
                }
                else -> {}
            }// no further action
        }
    }

    private fun Notification.Builder.updateNotificationAppSettings(includeIntent: Boolean) {
        (context.applicationContext as? RadarApplication)?.let { app ->
            setLargeIcon(app.largeIcon)
            setSmallIcon(app.smallIcon)
        }

        if (includeIntent) {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
            setContentIntent(pendingIntent)
        }

        setWhen(System.currentTimeMillis())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NotificationHandler::class.java)

        /** Notification channel ID for informational messages. No user response required.  */
        const val NOTIFICATION_CHANNEL_INFO = "org.radarbase.android.NotificationHandler.INFO"
        /**
         * Notification channel ID for tasks and active notification messages.
         * User response is requested.
         */
        const val NOTIFICATION_CHANNEL_NOTIFY = "org.radarbase.android.NotificationHandler.NOTIFY"
        /**
         * Notification channel ID for missed tasks and app failure notification messages.
         * User response is strongly requested.
         */
        const val NOTIFICATION_CHANNEL_ALERT = "org.radarbase.android.NotificationHandler.ALERT"
        /**
         * Notification channel ID for missed tasks and app failure notification messages.
         * User response is required.
         */
        const val NOTIFICATION_CHANNEL_FINAL_ALERT = "org.radarbase.android.NotificationHandler.FINAL_ALERT"
    }

    data class NotificationRegistration(private val manager: NotificationManager?, private val id: Int) {
        fun cancel() {
            manager?.cancel(id)
        }
    }
}
