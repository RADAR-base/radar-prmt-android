package org.radarcns.detail

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarbase.android.RadarConfiguration
import org.radarbase.android.auth.portal.ManagementPortalClient
import org.radarbase.producer.rest.RestClient
import org.radarcns.detail.DownloadFileFromUrl.Companion.DOWNLOADED_FILE
import org.radarcns.detail.OneTimeScheduleWorker.Companion.FROM_NOTIFICATION_KEY
import org.radarcns.detail.SettingsActivity.Companion.UPDATE_CHECK_FREQUENCY
import org.radarcns.detail.UpdateScheduledService.Companion.LAST_UPDATE_CHECK_TIMESTAMP
import org.radarcns.detail.UpdateScheduledService.Companion.UPDATE_VERSION_NAME_KEY
import org.radarcns.detail.UpdateScheduledService.Companion.UPDATE_VERSION_URL_KEY
import org.radarcns.detail.UpdateScheduledService.Companion.getCurrentVersion
import org.radarcns.detail.UpdateScheduledService.Companion.getNewVersion
import java.io.File
import java.util.*
//import java.net.Authenticator;
//import java.net.PasswordAuthentication;
//import java.net.URI;
//import okhttp3.OkHttpClient
//import okhttp3.Request


class UpdatesActivity : AppCompatActivity(), TaskDelegate {
    private lateinit var config: RadarConfiguration
    private var releasesUrl: String = ""
    private var lastUpdateCheckTimestamp: Long = 0

    private lateinit var currentVersion: TextView
    private lateinit var updateStatus: TextView

    private lateinit var updateLinearLayout: LinearLayout
    private lateinit var startDownloadingButton: Button
    private lateinit var cancelUpdateButton: Button
    private lateinit var stopNotificationButton: Button

    private lateinit var progressBarContainerLayout: ConstraintLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressBarPercent: TextView

    private lateinit var checkForUpdatesPBar: ProgressBar
    private lateinit var checkForUpdateButton: Button

    private lateinit var lastCheckStatus: TextView

    private lateinit var newVersionDownloadUrl: String
    private var firstTime = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_updates)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar).apply {
            setTitle(R.string.updates)
        })
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
        config = radarConfig

        config.config.observe(this, { config ->
            releasesUrl = config.getString(SettingsActivity.RELEASES_URL)
            lastUpdateCheckTimestamp = config.getLong(LAST_UPDATE_CHECK_TIMESTAMP, 0)
            println("firstTime: $firstTime")
            if(firstTime) {
                firstTime = false
                checkForUpdates()
            }
        })

        currentVersion = findViewById(R.id.current_version)
        updateStatus = findViewById(R.id.update_status)

        updateLinearLayout = findViewById(R.id.update_linearLayout)
        startDownloadingButton = findViewById(R.id.start_downloading_button)
        cancelUpdateButton = findViewById(R.id.cancel_update_button)
        stopNotificationButton = findViewById(R.id.stop_notification_button)

        progressBarContainerLayout = findViewById(R.id.progressbar_container)
        progressBar = findViewById(R.id.progressBar)
        progressBarPercent = findViewById(R.id.progressbar_percent)

        checkForUpdatesPBar = findViewById(R.id.check_for_updates_pbar)
        checkForUpdateButton = findViewById(R.id.check_for_updates_button)

        lastCheckStatus = findViewById(R.id.last_check_status)

        // TODO not all notifications should be canceled
        val notificationMng =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationMng.cancelAll()

        setCurrentVersion()

        setUpdateLastCheck()

        if (intent.extras?.getBoolean(FROM_NOTIFICATION_KEY, false) == true){
            updateLinearLayout.visibility = View.VISIBLE
            updateStatus.text = getString(
                R.string.new_version_available,
                getString(R.string.app_name),
                intent.extras?.getString(
                    "versionName",
                    ""
                )
            )
            stopNotificationButton.visibility = View.VISIBLE
        }

//        checkForUpdates()

        checkForUpdateButton.setOnClickListener {
            checkForUpdates()
        }

        startDownloadingButton.setOnClickListener {
            startDownloading()
        }

        cancelUpdateButton.setOnClickListener {
            finish()
        }

        stopNotificationButton.setOnClickListener {
            val updateCheckFrequency = SettingsActivity.WEEK
            config.put(UPDATE_CHECK_FREQUENCY, updateCheckFrequency)
            config.persistChanges()
            finish()
        }
    }

    private fun setCurrentVersion() {
        currentVersion.text = getString(R.string.currentVersion, getCurrentVersion(packageManager, packageName))
    }

    private fun setUpdateLastCheck() {
        val cal: Calendar = Calendar.getInstance(Locale.ENGLISH)
        cal.timeInMillis = lastUpdateCheckTimestamp
        val date: String = DateFormat.format(LAST_CHECK_DATE_FORMAT, cal).toString()
        lastCheckStatus.text = getString(R.string.last_check, date)
    }

    private fun checkForUpdates() {
        checkForUpdatesPBar.visibility = View.VISIBLE
        checkForUpdateButton.visibility = View.GONE

//        val url = releasesUrl
//
//        val client: RestClient = RestClient.newClient()
//            //.server(managementPortal)
//            .build()
//
//        val request = client.requestBuilder(url)
//            .header("Accept", ManagementPortalClient.APPLICATION_JSON)
//            .build()
//        val client = OkHttpClient()
//
//        val request: Request = Request.Builder()
//            .url("https://www.vogella.com/index.html")
//            .build()
//
//        client.newCall(request).enqueue(new Callback() {
//            @Override
//            public void onFailure(Call call, IOException e) {
//                e.printStackTrace();
//            }
//
//            @Override
//            public void onResponse(Call call, final Response response) throws IOException {
//                if (!response.isSuccessful()) {
//                    throw new IOException ("Unexpected code " + response);
//                } else {
//                    // do something wih the result
//                }
//            }
//        }

        val queue = Volley.newRequestQueue(this)
        val url = releasesUrl
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                config.put(LAST_UPDATE_CHECK_TIMESTAMP, System.currentTimeMillis())
                config.persistChanges()
                setUpdateLastCheck()

                val newVersion = getNewVersion(response, packageManager, packageName)
                if (newVersion !== null) {
                    val updateStatus: TextView = findViewById(R.id.update_status)
                    newVersionDownloadUrl = newVersion.get(UPDATE_VERSION_URL_KEY) as String
                    updateStatus.text = getString(
                        R.string.new_version_available,
                        getString(R.string.app_name),
                        newVersion.get(UPDATE_VERSION_NAME_KEY)
                    )
                } else {
                    val updateStatus: TextView = findViewById(R.id.update_status)
                    updateStatus.text = getString(
                        R.string.new_version_not_available, getString(
                            R.string.app_name
                        )
                    )
                }
                updateLinearLayout.visibility = View.VISIBLE
                checkForUpdatesPBar.visibility = View.GONE
                checkForUpdateButton.visibility = View.VISIBLE
            },
            {
                // show error
                Log.v("ScheduleService", "Error")
            })
        queue.add(stringRequest)
    }

    private fun startDownloading() {
        updateLinearLayout.visibility = View.GONE
        progressBarContainerLayout.visibility = View.VISIBLE
        checkForUpdateButton.visibility = View.GONE
        progressBar.progress = 0
        val downloader = DownloadFileFromUrl(this, this)
        downloader.setProgressBar(progressBar)
        val apkUrl = newVersionDownloadUrl
        downloader.execute(apkUrl)
    }

    private fun install() {
        val fileName = DOWNLOADED_FILE
        val fileLocation = File(filesDir, fileName)
        val uri = FileProvider.getUriForFile(
            this,
            applicationContext.packageName.toString() + PROVIDER_PATH,
            fileLocation
        )
        val install = Intent(Intent.ACTION_VIEW)
        install.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        install.setDataAndType(
            uri,
            MIME_TYPE
        )
        startActivity(install)
    }

    override fun taskCompletionResult(result: String?) {
        install()
        progressBarContainerLayout.visibility = View.GONE
        updateLinearLayout.visibility = View.VISIBLE
        checkForUpdateButton.visibility = View.VISIBLE
    }

    companion object {
        private const val MIME_TYPE = "application/vnd.android.package-archive"
        private const val PROVIDER_PATH = ".provider"
        private const val LAST_CHECK_DATE_FORMAT = "MMM dd, yyyy HH:mm:ss"
    }
}

