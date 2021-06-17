package org.radarcns.detail

import android.content.Intent
import android.util.Log
import androidx.lifecycle.LifecycleService
import java.util.*
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import java.util.concurrent.TimeUnit
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.json.JSONObject
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarcns.detail.UpdatesActivity.Companion.DAY
import org.radarcns.detail.UpdatesActivity.Companion.UPDATE_CHECK_PERIOD_KEY
import org.radarcns.detail.UpdatesActivity.Companion.UPDATE_RELEASES_URL_KEY
import org.radarcns.detail.UpdatesActivity.Companion.UPDATE_RELEASE_URL

class UpdateScheduledService: LifecycleService() {
    private var timerStarted: Boolean = false
    private var timer: Timer = Timer()

    private var updateNotificationConfig = true
    private var updateCheckPeriod: Long = DAY
    private var releasesUrl: String = ""

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
    }

    override fun onCreate() {
        super.onCreate()

        radarConfig.config.observe(this, { config ->
            updateCheckPeriod = config.getLong(UPDATE_CHECK_PERIOD_KEY, DAY)
            releasesUrl = config.getString(UPDATE_RELEASES_URL_KEY, UPDATE_RELEASE_URL)

            if (timerStarted) {
                timer.cancel()
                timerStarted = false
            }
            timer = Timer()
            timer.scheduleAtFixedRate(CheckUpdateTask(), updateCheckPeriod, updateCheckPeriod)
            timerStarted = true
        })
    }

    fun scheduleOneTimeNotification(initialDelay: Long, newUpdate: JSONObject) {
        val data = Data.Builder()
        data.putString(UPDATE_VERSION_URL_KEY, newUpdate.get(UPDATE_VERSION_URL_KEY) as String?)
        data.putString(UPDATE_VERSION_NAME_KEY, newUpdate.get(UPDATE_VERSION_NAME_KEY) as String?)
        val work =
            OneTimeWorkRequestBuilder<OneTimeScheduleWorker>()
                .setInputData(data.build())
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

        WorkManager.getInstance(this@UpdateScheduledService).enqueue(work)
    }

    inner class CheckUpdateTask : TimerTask() {
        override fun run() {
            val queue = Volley.newRequestQueue(this@UpdateScheduledService)
            val url = releasesUrl
            val stringRequest = StringRequest(
                Request.Method.GET, url,
                { response ->
                    val updatePackage = getUpdatePackage(this@UpdateScheduledService, response, packageName)
                    if (updatePackage != null && updateNotificationConfig) {
                        scheduleOneTimeNotification(0, updatePackage)
                    }
                },
                {
                    Log.v("ScheduleService", "Error")
                })
            queue.add(stringRequest)
        }
    }

    companion object {
        const val UPDATE_VERSION_URL_KEY = "updateVersionUrl"
        const val UPDATE_VERSION_NAME_KEY = "updateVersionName"
        const val LAST_UPDATE_CHECK_TIMESTAMP = "last_update_check_timestamp"
    }
}