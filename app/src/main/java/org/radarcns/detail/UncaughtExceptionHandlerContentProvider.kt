package org.radarcns.detail

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat.CATEGORY_ALARM
import org.radarbase.android.RadarApplication
import org.radarbase.android.RadarApplication.Companion.radarApp
import org.radarbase.android.util.NotificationHandler.Companion.NOTIFICATION_CHANNEL_ALERT
import org.radarbase.android.util.toPendingIntentFlag
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

class UncaughtExceptionHandlerContentProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            val logger = LoggerFactory.getLogger(RadarApplication::class.java)
            val currentTrace = ByteArrayOutputStream().use { byteOut ->
                PrintStream(byteOut).use { printOut ->
                    IllegalStateException("Stopped application due to unexpected exception").printStackTrace(printOut)
                }
                byteOut.toString("UTF-8")
            }
            logger.error("{}", currentTrace)
            logger.error("Uncaught error", ex)

            val enableRestart = (context?.applicationContext as? RadarApplicationImpl)
                ?.enableCrashRecovery
                ?: false
            if (enableRestart) {
                logger.error("Restarting app after crash")
                triggerRestart()
            } else {
                defaultHandler?.uncaughtException(thread, ex)
            }
            exitProcess(2)
        }
        return true
    }

    @SuppressLint("ImplicitSamInstance")
    private fun triggerRestart() {
        val currentContext = context ?: return

        currentContext.stopService(Intent(currentContext, RadarServiceImpl::class.java))

        if (!Settings.canDrawOverlays(currentContext)) {
            currentContext.radarApp.notificationHandler.notify(
                id = CRASH_RESTART_NOTIFICATION_ID,
                channel = NOTIFICATION_CHANNEL_ALERT,
                includeStartIntent = true,
            ) {
                currentContext.run {
                    setContentTitle(getString(R.string.crash_restart_title))
                    setContentTitle(getString(R.string.crash_restart_text))
                    setCategory(CATEGORY_ALARM)
                }
            }
            return
        }

        val intent = Intent(currentContext, SplashActivityImpl::class.java).apply {
            putExtra("crash", true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val activityOptionsBundle: Bundle? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().run {
                setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                toBundle()
            }
        } else null

        val pendingIntent = PendingIntent.getActivity(
            currentContext,
            231912,
            intent,
            PendingIntent.FLAG_ONE_SHOT.toPendingIntentFlag(),
            activityOptionsBundle
        )

        val alarmManager = currentContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 100,
            pendingIntent,
        )
    }

    override fun query(
        uri: Uri,
        projection: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String?>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String?>?,
    ): Int = 0

    companion object {
        const val CRASH_RESTART_NOTIFICATION_ID = 35003
    }

}
