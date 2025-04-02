package org.radarcns.detail

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarbase.android.RadarConfiguration
import org.radarbase.android.storage.extract.DatabaseToCSVExtractor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class SettingsActivity : AppCompatActivity() {
    private lateinit var config: RadarConfiguration
    private lateinit var progressOverlay: FrameLayout
    private val csvExtractor: DatabaseToCSVExtractor = DatabaseToCSVExtractor()
    private var responsiveOnTouch: Boolean = true

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        config = radarConfig
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar).apply {
            setTitle(R.string.settings)
        })
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        val enableDataButton: SwitchCompat = findViewById(R.id.enable_data_switch)
        enableDataButton.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                config.put(RadarConfiguration.SEND_ONLY_WITH_WIFI, !isChecked)
                config.persistChanges()
            }
        }
        val enableDataPriorityButton: SwitchCompat = findViewById(R.id.enable_data_high_priority_switch)
        enableDataPriorityButton.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                config.put(RadarConfiguration.SEND_OVER_DATA_HIGH_PRIORITY, isChecked)
                config.persistChanges()
            }
        }

        lifecycleScope.launch {
            config.config.collect { config ->
                val useData = !config.getBoolean(RadarConfiguration.SEND_ONLY_WITH_WIFI, true)
                val useHighPriority =
                    config.getBoolean(RadarConfiguration.SEND_OVER_DATA_HIGH_PRIORITY, false)
                enableDataButton.isChecked = useData
                enableDataPriorityButton.isEnabled = useData
                enableDataPriorityButton.isChecked = useData && useHighPriority
            }
        }

        findViewById<MaterialButton>(R.id.reset_settings_button).setOnClickListener { v -> startReset(v) }
        findViewById<MaterialButton>(R.id.share_app_status).setOnClickListener {
            lifecycleScope.launch {
                emailApplicationStatuses()
            }
        }
        progressOverlay = findViewById(R.id.progress_overlay)

        let(csvExtractor::initialize)
    }

    private fun startReset(@Suppress("UNUSED_PARAMETER") view: View) {
        AlertDialog.Builder(this).apply {
            setTitle("Reset")
            setMessage("Do you really want to reset to default settings?")
            setIcon(android.R.drawable.ic_dialog_alert)
            setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    config.reset(*MANAGED_SETTINGS)
                }
            }
            setNegativeButton(android.R.string.cancel, null)
        }.show()
    }

    private suspend fun emailApplicationStatuses() {
        try {
            enableProcessingUI()
            withContext(Dispatchers.Default) {
                csvExtractor.exportEntities()
            }
        } catch (ex: IllegalStateException) {
            logger.error("Failed to send debug payload files: {}", ex.message)
            Toast.makeText(this, "Failed to share the files", Toast.LENGTH_LONG).show()
            return
        } finally {
            disableProcessingUI()
        }

        val authority = "${packageName}.provider"

        val payloadFileUris: List<Uri> = listOf(
            File(filesDir, SOURCE_STATUS_FILE_PATH), File(filesDir, NETWORK_STATUS_FILE_PATH)
        ).let { files ->
            files.map {
                FileProvider.getUriForFile(
                    this,
                    authority,
                    it
                )
            }
        }

        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = TEXT_CSV
            putExtra(Intent.EXTRA_EMAIL, "rb-test@kcl.ac.uk")
            putExtra(Intent.EXTRA_SUBJECT, DEBUG_PAYLOAD_SUBJECT)
            putCharSequenceArrayListExtra(Intent.EXTRA_TEXT, arrayListOf(DEBUG_PAYLOAD_BODY))
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(payloadFileUris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.also {
            startActivity(Intent.createChooser(it, "Send email via:"))
        }
    }

    private fun enableProcessingUI() {
        progressOverlay.visibility = View.VISIBLE
        if (responsiveOnTouch) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
            responsiveOnTouch = false
        }
    }

    private fun disableProcessingUI() {
        progressOverlay.visibility = View.GONE
        if (!responsiveOnTouch) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            responsiveOnTouch = true
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(SettingsActivity::class.java)

        private val MANAGED_SETTINGS = arrayOf(
            RadarConfiguration.SEND_ONLY_WITH_WIFI,
            RadarConfiguration.SEND_OVER_DATA_HIGH_PRIORITY,
        )

        private const val TEXT_CSV = "text/csv"
        private const val FILE_NAME_PREFIX = "application_status_"
        private const val FILE_EXTENSION = ".csv"
        private const val APPLICATION_PLUGIN_EXPORT_PATH = "org.radarbase.monitor.application.exports"
        private const val NETWORK_STATUS_FILE_PATH = "${APPLICATION_PLUGIN_EXPORT_PATH}/${FILE_NAME_PREFIX}network${FILE_EXTENSION}"
        private const val SOURCE_STATUS_FILE_PATH = "${APPLICATION_PLUGIN_EXPORT_PATH}/${FILE_NAME_PREFIX}source${FILE_EXTENSION}"
        private const val DEBUG_PAYLOAD_SUBJECT = "RADAR pRMT Application Status Data"
        private const val DEBUG_PAYLOAD_BODY = "Please find attached the exported CSV data for plugins and network status."
    }
}
