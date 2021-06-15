package org.radarcns.detail

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarbase.android.RadarConfiguration
import java.util.*

class SettingsActivity : AppCompatActivity() {
    private lateinit var config: RadarConfiguration

    private lateinit var updateCheckTimeTextView: TextView
    private var tHour: Int = 0
    private var tMinute: Int = 0
    private lateinit var updateCheckTimePickerDialog: TimePickerDialog
    private lateinit var radioHour: RadioButton
    private lateinit var radioDay: RadioButton
    private lateinit var radioWeek: RadioButton

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        config = radarConfig
        setContentView(R.layout.activity_settings)

        updateCheckTimeTextView = findViewById(R.id.update_check_time_value)

        radioHour = findViewById(R.id.radio_hour)
        radioDay = findViewById(R.id.radio_day)
        radioWeek = findViewById(R.id.radio_week)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar).apply {
            setTitle(R.string.settings)
        })
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        updateCheckTimePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                tHour = hourOfDay
                tMinute = minute
                val calendar = Calendar.getInstance()
                calendar[0, 0, 0, tHour] = tMinute
                updateCheckTimeTextView.text = DateFormat.format("HH:mm", calendar)
                config.put(UPDATE_CHECK_TIME, tHour * 60 + tMinute)
                config.persistChanges()
            }, 12, 0, true
        )

        val enableDataButton: SwitchCompat = findViewById(R.id.enableDataSwitch)
        enableDataButton.setOnCheckedChangeListener { _, isChecked ->
            config.put(RadarConfiguration.SEND_ONLY_WITH_WIFI, !isChecked)
            config.persistChanges()
        }
        val enableDataPriorityButton: SwitchCompat = findViewById(R.id.enableDataHighPrioritySwitch)
        enableDataPriorityButton.setOnCheckedChangeListener { _, isChecked ->
            config.put(RadarConfiguration.SEND_OVER_DATA_HIGH_PRIORITY, isChecked)
            config.persistChanges()
        }

        val enableUpdateCheckButton: SwitchCompat = findViewById(R.id.update_check_switch)
        enableUpdateCheckButton.setOnCheckedChangeListener { _, isChecked ->
            config.put(UPDATE_CHECK, isChecked)
            config.persistChanges()
        }
        val enableUpdateCheckNotificationButton: SwitchCompat = findViewById(R.id.update_check_notification_switch)
        enableUpdateCheckNotificationButton.setOnCheckedChangeListener { _, isChecked ->
            config.put(UPDATE_CHECK_NOTIFICATION, isChecked)
            config.persistChanges()
        }

        config.config.observe(this, { config ->
            val useData = !config.getBoolean(RadarConfiguration.SEND_ONLY_WITH_WIFI, true)
            val useHighPriority = config.getBoolean(RadarConfiguration.SEND_OVER_DATA_HIGH_PRIORITY, false)

            val updateCheck = config.getBoolean(UPDATE_CHECK, true)
            val updateCheckNotification = config.getBoolean(UPDATE_CHECK_NOTIFICATION, true)

            val updateCheckFrequency = config.getLong(UPDATE_CHECK_FREQUENCY, DAY)
            val updateCheckTime = config.getInt(UPDATE_CHECK_TIME, DEFAULT_UPDATE_CHECK_TIME)

            enableDataButton.isChecked = useData
            enableDataPriorityButton.isEnabled = useData
            enableDataPriorityButton.isChecked = useData && useHighPriority

            enableUpdateCheckButton.isChecked = updateCheck
            enableUpdateCheckNotificationButton.isEnabled = updateCheck
            enableUpdateCheckNotificationButton.isChecked = updateCheck && updateCheckNotification

            val updateCheckFrequencyLayout: LinearLayout = findViewById(R.id.update_check_frequency_layout)
            val updateCheckTimeLayout: LinearLayout = findViewById(R.id.update_check_time_layout)
            if(updateCheck){
                updateCheckFrequencyLayout.visibility = View.VISIBLE
                updateCheckTimeLayout.visibility = View.VISIBLE
            }else{
                updateCheckFrequencyLayout.visibility = View.GONE
                updateCheckTimeLayout.visibility = View.GONE
            }
            setUpdateCheckRadioGroup(updateCheckFrequency)
            setUpdateCheckTimeView(updateCheckTime)
        })
    }

    private fun setUpdateCheckRadioGroup(updateCheckFrequency: Long){
        radioHour.isChecked = updateCheckFrequency == HOUR
        radioDay.isChecked = updateCheckFrequency == DAY
        radioWeek.isChecked = updateCheckFrequency == WEEK
    }

    private fun setUpdateCheckTimeView(updateCheckTime: Int){
        val calendar = Calendar.getInstance()
        tHour = updateCheckTime / 60
        tMinute = updateCheckTime % 60
        calendar[0, 0, 0, tHour] = tMinute
        updateCheckTimeTextView.text = DateFormat.format("HH:mm aa", calendar)
    }

    fun onUpdateCheckFrequencyRadioButtonClicked(view: View) {
        var updateCheckFrequency = DAY
        if (view is RadioButton) {
            val checked = view.isChecked
            when (view.getId()) {
                R.id.radio_hour ->
                    if (checked) {
                        updateCheckFrequency = HOUR
                    }
                R.id.radio_day ->
                    if (checked) {
                        updateCheckFrequency = DAY
                    }
                R.id.radio_week ->
                    if (checked) {
                        updateCheckFrequency = WEEK
                    }
            }

            config.put(UPDATE_CHECK_FREQUENCY, updateCheckFrequency)
            config.persistChanges()
        }
    }

    fun showUpdateCheckTimePickerDialog(v: View) {
        updateCheckTimePickerDialog.updateTime(tHour, tMinute)
        updateCheckTimePickerDialog.show()
    }

    fun startReset(@Suppress("UNUSED_PARAMETER") view: View) {
        AlertDialog.Builder(this).apply {
            setTitle("Reset")
            setMessage("Do you really want to reset to default settings?")
            setIcon(android.R.drawable.ic_dialog_alert)
            setPositiveButton(android.R.string.ok) { _, _ ->
                config.reset(*MANAGED_SETTINGS)
            }
            setNegativeButton(android.R.string.cancel, null)
        }.show()
    }

    companion object {
        private val MANAGED_SETTINGS = arrayOf(
            RadarConfiguration.SEND_ONLY_WITH_WIFI,
            RadarConfiguration.SEND_OVER_DATA_HIGH_PRIORITY,
        )

        const val UPDATE_CHECK = "update_check"
        const val UPDATE_CHECK_NOTIFICATION = "update_check_notification"
        const val UPDATE_CHECK_FREQUENCY = "update_check_frequency"
        const val UPDATE_CHECK_TIME = "update_check_time"
        const val RELEASES_URL = "releases_url"

        const val DEFAULT_UPDATE_CHECK_TIME = 720 // 0 - 1439 => 720: 12.00 pm
        const val HOUR = 15 * 1000L // 60 * 60 * 1000L
        const val DAY = 2 * HOUR //24 * HOUR
        const val WEEK = 2 * DAY // 7 * DAY
        const val RELEASE_URL = "https://api.github.com/repos/peyman-mohtashami/auto-update-test/releases"
    }
}
