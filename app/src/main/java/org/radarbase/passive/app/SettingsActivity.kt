package org.radarbase.passive.app

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import org.radarbase.android.RadarConfiguration
import org.radarbase.android.radarConfig

class SettingsActivity : AppCompatActivity() {
    private lateinit var enableDataButton: Switch
    private lateinit var enableDataPriorityButton: Switch
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

        enableDataButton = findViewById(R.id.enableDataSwitch)
        enableDataButton.setOnCheckedChangeListener { _, isChecked ->
            config.put(RadarConfiguration.SEND_ONLY_WITH_WIFI, !isChecked)
            config.persistChanges()
            enableDataPriorityButton.isEnabled = isChecked
        }
        enableDataPriorityButton = findViewById(R.id.enableDataHighPrioritySwitch)
        enableDataPriorityButton.setOnCheckedChangeListener { _, isChecked ->
            config.put(RadarConfiguration.SEND_OVER_DATA_HIGH_PRIORITY, isChecked)
            config.persistChanges()
        }
    }

    override fun onStart() {
        super.onStart()
        updateView()
    }

    private fun updateView() {
        enableDataButton.isChecked = !config.getBoolean(RadarConfiguration.SEND_ONLY_WITH_WIFI, RadarConfiguration.SEND_ONLY_WITH_WIFI_DEFAULT)
        enableDataPriorityButton.isChecked = config.getBoolean(RadarConfiguration.SEND_OVER_DATA_HIGH_PRIORITY, RadarConfiguration.SEND_ONLY_WITH_WIFI_DEFAULT)
    }

    fun startReset(@Suppress("UNUSED_PARAMETER") view: View) {
        AlertDialog.Builder(this).apply {
            setTitle("Reset")
            setMessage("Do you really want to reset to default settings?")
            setIcon(android.R.drawable.ic_dialog_alert)
            setPositiveButton(android.R.string.yes) { _, _ ->
                config.reset(*MANAGED_SETTINGS)
                updateView()
            }
            setNegativeButton(android.R.string.no, null)
        }.show()
    }

    companion object {

        private val MANAGED_SETTINGS = arrayOf(RadarConfiguration.SEND_ONLY_WITH_WIFI, RadarConfiguration.SEND_OVER_DATA_HIGH_PRIORITY)
    }
}
