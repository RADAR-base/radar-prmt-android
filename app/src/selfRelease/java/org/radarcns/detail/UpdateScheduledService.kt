package org.radarcns.detail

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import android.util.Log
//import android.view.View
//import android.widget.TextView
import androidx.lifecycle.LifecycleService
import java.util.*
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
//import com.android.volley.Request
//import com.android.volley.toolbox.StringRequest
//import com.android.volley.toolbox.Volley
import java.util.concurrent.TimeUnit
import kotlinx.serialization.*
import kotlinx.serialization.json.*
//import okhttp3.*
import org.json.JSONObject
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarcns.detail.SettingsActivity.Companion.DAY
import org.radarcns.detail.SettingsActivity.Companion.DEFAULT_UPDATE_CHECK_TIME
import org.radarcns.detail.SettingsActivity.Companion.RELEASES_URL
import org.radarcns.detail.SettingsActivity.Companion.RELEASE_URL
import org.radarcns.detail.SettingsActivity.Companion.UPDATE_CHECK
import org.radarcns.detail.SettingsActivity.Companion.UPDATE_CHECK_FREQUENCY
import org.radarcns.detail.SettingsActivity.Companion.UPDATE_CHECK_NOTIFICATION
import org.radarcns.detail.SettingsActivity.Companion.UPDATE_CHECK_TIME
//import java.io.IOException

class UpdateScheduledService: LifecycleService() {
    private var timerStarted: Boolean = false
    private var timer: Timer = Timer()

    private var updateNotificationConfig = true
    private var updateCheckPeriod: Long = DAY
    private var updateCheckTime: Int = DEFAULT_UPDATE_CHECK_TIME
    private var releasesUrl: String = ""

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
    }

    override fun onCreate() {
        super.onCreate()

        radarConfig.config.observe(this, { config ->
            updateNotificationConfig = config.getBoolean(UPDATE_CHECK_NOTIFICATION, true)
            updateCheckPeriod = config.getLong(UPDATE_CHECK_FREQUENCY, DAY)
            updateCheckTime = config.getInt(UPDATE_CHECK_TIME, DEFAULT_UPDATE_CHECK_TIME)
            releasesUrl = config.getString(RELEASES_URL, RELEASE_URL)

            val now = Calendar.getInstance()
            val firstTime = Calendar.getInstance()
            val hour = updateCheckTime / 60
            val minute = updateCheckTime % 60
            println(firstTime.get(Calendar.YEAR))
            firstTime[firstTime.get(Calendar.YEAR), firstTime.get(Calendar.MONTH), firstTime.get(Calendar.DATE), hour] = minute

            if(firstTime.before(now)){
                firstTime.add(Calendar.HOUR_OF_DAY, 24)
            }

            if (timerStarted) {
                timer.cancel()
                timerStarted = false
            }
            if(config.getBoolean(UPDATE_CHECK, true)){
                timer = Timer()
//                timer.scheduleAtFixedRate(CheckUpdateTask(), firstTime.time, updateCheckPeriod)
                 timer.scheduleAtFixedRate(CheckUpdateTask(), updateCheckPeriod, updateCheckPeriod)
                timerStarted = true
            }
        })
    }

    fun scheduleOneTimeNotification(initialDelay: Long, newUpdate: JSONObject) { //url: String, version: String) {
        val data = Data.Builder()
        //Add parameter in Data class. just like bundle. You can also add Boolean and Number in parameter.
        data.putString(UPDATE_VERSION_URL_KEY, newUpdate.get(UPDATE_VERSION_URL_KEY) as String?) // url)
        data.putString(UPDATE_VERSION_NAME_KEY, newUpdate.get(UPDATE_VERSION_NAME_KEY) as String?) // version)
        val work =
            OneTimeWorkRequestBuilder<OneTimeScheduleWorker>()
                .setInputData(data.build())
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

        WorkManager.getInstance(this@UpdateScheduledService).enqueue(work)
    }

    inner class CheckUpdateTask : TimerTask() {
        override fun run() {
            println("CheckUpdate run")
            val queue = Volley.newRequestQueue(this@UpdateScheduledService)
            val url = releasesUrl
            val stringRequest = StringRequest(
                Request.Method.GET, url,
                { response ->
//                    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@UpdateScheduledService)
//                    with (prefs.edit()) {
//                        putLong(LAST_UPDATE_CHECK_TIMESTAMP, System.currentTimeMillis())
//                        apply()
//                    }
                    println("CheckUpdate Notif")
                    val newUpdate = getNewVersion(response, packageManager, packageName)
                    if (newUpdate !== null && updateNotificationConfig) {
                        scheduleOneTimeNotification(
                            0,
                            newUpdate
                        )
                    }

//                    config.put(LAST_UPDATE_CHECK_TIMESTAMP, System.currentTimeMillis())
//                    config.persistChanges()
//                    setUpdateLastCheck()
//
//                    val newVersion = getNewVersion(response, packageManager, packageName)
//                    if (newVersion !== null) {
//                        val updateStatus: TextView = findViewById(R.id.update_status)
//                        newVersionDownloadUrl = newVersion.get(UPDATE_VERSION_URL_KEY) as String
//                        updateStatus.text = getString(
//                            R.string.new_version_available,
//                            getString(R.string.app_name),
//                            newVersion.get(UPDATE_VERSION_NAME_KEY)
//                        )
//                    } else {
//                        val updateStatus: TextView = findViewById(R.id.update_status)
//                        updateStatus.text = getString(
//                            R.string.new_version_not_available, getString(
//                                R.string.app_name
//                            )
//                        )
//                    }
//                    updateLinearLayout.visibility = View.VISIBLE
//                    checkForUpdatesPBar.visibility = View.GONE
//                    checkForUpdateButton.visibility = View.VISIBLE
                },
                {
                    // show error
                    Log.v("ScheduleService", "Error")
                })
            queue.add(stringRequest)


//            val url = releasesUrl
//            val client = OkHttpClient()
//
//            val request: Request = Request.Builder()
//                .url(url)
//                .build()
//
//            client.newCall(request).enqueue(object: Callback {
//
//                override fun onFailure(call: Call, e: IOException) {
//                    Log.v("ScheduleService", "Error")
//                    e.printStackTrace();
//                }
//
//                override fun onResponse(call: Call, response: Response) {
//                    if (!response.isSuccessful) {
//                        Log.v("ScheduleService", "Error")
//                        throw IOException("Unexpected code $response")
//
//                    } else {
//                        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@UpdateScheduledService)
//                        with (prefs.edit()) {
//                            putLong(LAST_UPDATE_CHECK_TIMESTAMP, System.currentTimeMillis())
//                            apply()
//                        }
//                        val newUpdate = getNewVersion(response, packageManager, packageName)
//                        if (newUpdate !== null && updateNotificationConfig) {
//                            scheduleOneTimeNotification(
//                                0,
//                                newUpdate
//                            )
//                        }
//                    }
//                }
//            })
        }
    }

    companion object {
        const val UPDATE_VERSION_URL_KEY = "updateVersionUrl"
        const val UPDATE_VERSION_NAME_KEY = "updateVersionName"
        const val LAST_UPDATE_CHECK_TIMESTAMP = "last_update_check_timestamp"

        fun getCurrentVersion(packageManager: PackageManager, packageName: String): String? {
            var pInfo: PackageInfo? = null
            try {
                pInfo = packageManager.getPackageInfo(packageName, 0)
            } catch (e1: PackageManager.NameNotFoundException) {
                e1.printStackTrace()
            }
            return pInfo!!.versionName
        }

        fun getNewVersion(response: String, packageManager: PackageManager, packageName: String): JSONObject? {
            val currentVersion = getCurrentVersion(packageManager, packageName)
            val responseObject = Json.parseToJsonElement(response)
            if (responseObject.jsonArray.size > 0) {
                val latestRelease = responseObject.jsonArray[0]
                val tagName = latestRelease.jsonObject["tag_name"]?.toString()?.replace("\"", "")
                if (!tagName.equals(currentVersion)) {
                    val newVersion = JSONObject()
                    newVersion.put(
                        UPDATE_VERSION_URL_KEY,
                        latestRelease.jsonObject["assets"]?.jsonArray?.get(0)?.jsonObject?.getValue(
                            "browser_download_url"
                        )
                            .toString().replace("\"", "")
                    )
                    newVersion.put(UPDATE_VERSION_NAME_KEY, tagName)

                    return newVersion
                }
            }
            return null
        }
    }
}