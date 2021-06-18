package org.radarcns.detail

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import org.radarcns.detail.DownloadFileFromUrl.Companion.DOWNLOADED_FILE
import org.radarcns.detail.OneTimeScheduleWorker.Companion.FROM_NOTIFICATION_KEY
import org.radarcns.detail.UpdateScheduledService.Companion.LAST_UPDATE_CHECK_TIMESTAMP
import org.radarcns.detail.UpdateScheduledService.Companion.UPDATE_VERSION_NAME_KEY
import org.radarcns.detail.UpdateScheduledService.Companion.UPDATE_VERSION_URL_KEY
import java.io.File

class UpdatesActivity : AppCompatActivity(), TaskDelegate {
    private lateinit var config: RadarConfiguration
    private var releasesUrl: String? = null

    private lateinit var currentVersion: TextView
    private lateinit var updateStatus: TextView

    private lateinit var updateLinearLayout: LinearLayout
    private lateinit var startDownloadingButton: Button
    private lateinit var cancelUpdateButton: Button
    private lateinit var stopNotificationButton: TextView

    private lateinit var progressBarContainerLayout: ConstraintLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressBarPercent: TextView

    private lateinit var newVersionApkUrl: String

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
            val newReleaseUrl = config.getString(UPDATE_RELEASES_URL_KEY)
            if( releasesUrl != newReleaseUrl ) {
                releasesUrl = newReleaseUrl
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

        // TODO not all notifications should be canceled
        val notificationMng =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationMng.cancelAll()

        setCurrentVersion()

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

        startDownloadingButton.setOnClickListener {
            startDownloading()
        }

        cancelUpdateButton.setOnClickListener {
            finish()
        }

        stopNotificationButton.setOnClickListener {
            config.put(UPDATE_CHECK_PERIOD_KEY, WEEK)
            config.persistChanges()
            finish()
        }
    }

    private fun setCurrentVersion() {
        currentVersion.text = getString(R.string.currentVersion, getInstalledPackageVersion(this))
    }

    private fun checkForUpdates() {
        val queue = Volley.newRequestQueue(this)
        val url = releasesUrl
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                config.put(LAST_UPDATE_CHECK_TIMESTAMP, System.currentTimeMillis())
                config.persistChanges()

                val updatePackage = getUpdatePackage(this, response)
                if (updatePackage != null) {
                    val updateStatus: TextView = findViewById(R.id.update_status)
                    newVersionApkUrl = updatePackage.get(UPDATE_VERSION_URL_KEY) as String
                    updateStatus.text = getString(
                        R.string.new_version_available,
                        getString(R.string.app_name),
                        updatePackage.get(UPDATE_VERSION_NAME_KEY)
                    )
                    updateLinearLayout.visibility = View.VISIBLE
                } else {
                    val updateStatus: TextView = findViewById(R.id.update_status)
                    updateStatus.text = getString(
                        R.string.new_version_not_available, getString(
                            R.string.app_name
                        )
                    )
                    updateLinearLayout.visibility = View.GONE
                }
            },
            {
                Log.v("ScheduleService", "Error")
            })
        queue.add(stringRequest)
    }

    private fun startDownloading() {
        updateLinearLayout.visibility = View.GONE
        progressBarContainerLayout.visibility = View.VISIBLE
        progressBar.progress = 0
        val downloader = DownloadFileFromUrl(this, this)
        downloader.setProgressBar(progressBar)
        downloader.execute(newVersionApkUrl)
    }

    private fun isInstallApkValid(): Boolean {
        val fileName = DOWNLOADED_FILE
        val fileLocation = File(filesDir, fileName)

        return isPackageNameSame(this, fileLocation.absolutePath)
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
        if(isInstallApkValid()) {
            install()
            progressBarContainerLayout.visibility = View.GONE
            updateLinearLayout.visibility = View.VISIBLE
        }else{
            updateStatus.text = getString(R.string.error_in_package_name_or_signature)
            progressBarContainerLayout.visibility = View.GONE
        }
    }

    companion object {
        private const val MIME_TYPE = "application/vnd.android.package-archive"
        private const val PROVIDER_PATH = ".provider"
        private const val HOUR = 60 * 60 * 1000L
        const val DAY = 24 * HOUR
        const val WEEK = 7 * DAY
        const val UPDATE_RELEASES_URL_KEY = "update_releases_url"
        const val UPDATE_CHECK_PERIOD_KEY = "update_check_period"
    }
}

