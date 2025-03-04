package org.radarcns.detail

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarbase.android.RadarConfiguration

class SettingsActivity : AppCompatActivity() {
    private lateinit var config: RadarConfiguration

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

    companion object {
        private val MANAGED_SETTINGS = arrayOf(
            RadarConfiguration.SEND_ONLY_WITH_WIFI,
            RadarConfiguration.SEND_OVER_DATA_HIGH_PRIORITY,
        )
    }
}
