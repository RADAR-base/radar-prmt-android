package org.radarcns.detail
/*
import android.content.Intent
import androidx.lifecycle.LifecycleService
import java.util.*
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarcns.detail.UpdatesActivity.Companion.DAY
import org.radarcns.detail.UpdatesActivity.Companion.UPDATE_CHECK_INTERVAL_KEY
import org.radarcns.detail.UpdatesActivity.Companion.UPDATE_RELEASES_URL_KEY
import java.io.IOException

class UpdateScheduledService: LifecycleService() {
    private var timerStarted: Boolean = false
    private var timer: Timer = Timer()

    private var updateNotificationConfig = true
    private var updateCheckPeriod: Long = DAY
    private var releasesUrl: String? = null

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
    }

    override fun onCreate() {
        super.onCreate()

        radarConfig.config.observe(this, { config ->
            updateCheckPeriod = config.getLong(UPDATE_CHECK_INTERVAL_KEY, DAY)
            releasesUrl = config.getString(UPDATE_RELEASES_URL_KEY)

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
            val url = releasesUrl
            if(url != null){
                val request = okhttp3.Request.Builder().url(url).build()

                val client = OkHttpClient()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!response.isSuccessful) throw IOException("Unexpected code $response")
                            val responseBody = response.body!!.string()
                            val updatePackage = getUpdatePackage(this@UpdateScheduledService, responseBody)
                            if (updatePackage != null && updateNotificationConfig) {
                                scheduleOneTimeNotification(0, updatePackage)
                            }
                        }
                    }
                })
            }
        }
    }

    companion object {
        const val UPDATE_VERSION_URL_KEY = "updateVersionUrl"
        const val UPDATE_VERSION_NAME_KEY = "updateVersionName"
        const val CONTACT_PHONE_KEY = "contact_phone"
        const val CONTACT_EMAIL_KEY = "contact_email"
        const val LAST_UPDATE_CHECK_TIMESTAMP = "last_update_check_timestamp"
    }
}*/