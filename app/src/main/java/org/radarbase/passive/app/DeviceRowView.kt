/*
 * Copyright 2017 The Hyve
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarbase.passive.app

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.radarbase.android.MainActivity
import org.radarbase.android.device.BaseDeviceState
import org.radarbase.android.device.DeviceServiceConnection
import org.radarbase.android.device.DeviceServiceProvider
import org.radarbase.android.device.DeviceStatusListener
import org.radarbase.android.util.Boast
import org.radarbase.passive.app.MainActivityViewImpl.Companion.truncate
import org.slf4j.LoggerFactory

/**
 * Displays a single device row.
 */
class DeviceRowView internal constructor(private val mainActivity: MainActivity, provider: DeviceServiceProvider<*>, root: ViewGroup) {

    private val connection: DeviceServiceConnection<*> = provider.connection
    private val mStatusIcon: View
    private val mBatteryLabel: ImageView
    private val mDeviceNameLabel: TextView
    private val devicePreferences: SharedPreferences
    private var filter: String
    private var state: BaseDeviceState? = null
    private var deviceName: String? = null
    private var previousBatteryLevel = java.lang.Float.NaN
    private var previousName: String? = null
    private var previousStatus: DeviceStatusListener.Status? = null

    init {
        devicePreferences = this.mainActivity.getSharedPreferences("device." + connection.serviceClassName, Context.MODE_PRIVATE)
        logger.info("Creating device row for provider {} and connection {}", provider, connection)
        val inflater = this.mainActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.activity_overview_device_row, root)

        (root.getChildAt(root.childCount - 1) as TableRow).apply {
            mStatusIcon = findViewById(R.id.status_icon)
            mDeviceNameLabel = findViewById(R.id.deviceName_label)
            mBatteryLabel = findViewById(R.id.battery_label)
            findViewById<Button>(R.id.inputDeviceButton).apply {
                if (provider.isFilterable) {
                    setOnClickListener { dialogDeviceName() }
                    isEnabled = true
                }
                text = provider.displayName
            }
            findViewById<View>(R.id.refreshButton)
                    .setOnClickListener { reconnectDevice() }
        }

        filter = ""
        setFilter(devicePreferences.getString("filter", "") ?: "")
    }

    private fun dialogDeviceName() {
        val input = EditText(mainActivity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(filter)
        }

        AlertDialog.Builder(this.mainActivity).apply {
            setTitle(R.string.filter_title)
            setPositiveButton(R.string.ok) { _, _ -> setFilter(input.text.toString().trim { it <= ' ' }) }
            setNegativeButton(R.string.cancel, null)
            setView(LinearLayout(mainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(70, 0, 70, 0)

                // Label
                addView(TextView(mainActivity).apply {
                    setText(R.string.filter_help_label)
                })

                addView(input)
            })
        }.show()
    }

    private fun setFilter(newValue: String) {
        if (filter == newValue) {
            logger.info("device filter did not change - ignoring")
            return
        }
        // Set new value and process
        filter = newValue
        devicePreferences.edit().putString("filter", filter).apply()

        val splitRegex = this.mainActivity.getString(R.string.filter_split_regex)
        val allowed = filter.split(splitRegex.toRegex())

        logger.info("setting device filter {}", allowed)

        mainActivity.radarService?.setAllowedDeviceIds(connection, allowed)
    }

    private fun reconnectDevice() {
        try {
            // will restart scanning after disconnect
            if (connection.isRecording) {
                connection.stopRecording()
            }
        } catch (iobe: IndexOutOfBoundsException) {
            Boast.makeText(this.mainActivity, "Could not restart scanning, there is no valid row index associated with this button.", Toast.LENGTH_LONG).show()
            logger.warn(iobe.message)
        }
    }

    fun update() {
        if (connection.hasService()) {
            state = connection.deviceData
            if (state != null) {
                val status = state!!.status
                when (status) {
                    DeviceStatusListener.Status.CONNECTED, DeviceStatusListener.Status.CONNECTING -> deviceName = connection.deviceName
                    else -> deviceName = null
                }
            }
        } else {
            state = null
            deviceName = null
        }
        if (deviceName != null) {
            deviceName = deviceName!!.replace("Empatica", "").trim { it <= ' ' }
        }
    }

    fun display() {
        updateBattery()
        updateDeviceName()
        updateDeviceStatus()
    }

    private fun updateDeviceStatus() {
        // Connection status. Change icon used.
        val status: DeviceStatusListener.Status
        if (state == null) {
            status = DeviceStatusListener.Status.DISCONNECTED
        } else {
            status = state!!.status
        }
        if (status != previousStatus) {
            logger.info("Device status is {}", status)
            previousStatus = status

            mStatusIcon.setBackgroundResource(when(status) {
                DeviceStatusListener.Status.CONNECTED -> R.drawable.status_connected
                DeviceStatusListener.Status.DISCONNECTED -> R.drawable.status_disconnected
                DeviceStatusListener.Status.READY -> R.drawable.status_searching
                DeviceStatusListener.Status.CONNECTING -> R.drawable.status_searching
                else -> deviceStatusIconDefault
            })
        }
    }

    private fun updateBattery() {
        // Battery levels observed for E4 are 0.01, 0.1, 0.45 or 1
        val batteryLevel = if (state == null) java.lang.Float.NaN else state!!.batteryLevel
        if (previousBatteryLevel == batteryLevel) {
            return
        }
        previousBatteryLevel = batteryLevel
        when {
            java.lang.Float.isNaN(batteryLevel) -> mBatteryLabel.setImageResource(R.drawable.ic_battery_unknown)
            batteryLevel < 0.1 -> mBatteryLabel.setImageResource(R.drawable.ic_battery_empty)
            batteryLevel < 0.3 -> mBatteryLabel.setImageResource(R.drawable.ic_battery_low)
            batteryLevel < 0.6 -> mBatteryLabel.setImageResource(R.drawable.ic_battery_50)
            batteryLevel < 0.85 -> mBatteryLabel.setImageResource(R.drawable.ic_battery_80)
            else -> mBatteryLabel.setImageResource(R.drawable.ic_battery_full)
        }
    }

    private fun updateDeviceName() {
        if (deviceName == previousName) {
            return
        }
        previousName = deviceName

        // \u2014 == —
        mDeviceNameLabel.text = if (deviceName == null) "\u2014" else truncate(deviceName, MAX_UI_DEVICE_NAME_LENGTH)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeviceRowView::class.java)
        private const val MAX_UI_DEVICE_NAME_LENGTH = 10
        private const val deviceStatusIconDefault = R.drawable.status_searching
    }
}
