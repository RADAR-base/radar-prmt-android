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

package org.radarcns.detail

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.radarbase.android.MainActivity
import org.radarbase.android.source.BaseSourceState
import org.radarbase.android.source.SourceProvider
import org.radarbase.android.source.SourceServiceConnection
import org.radarbase.android.source.SourceStatusListener
import org.radarbase.android.util.Boast
import org.radarbase.android.util.ChangeRunner
import org.radarcns.detail.MainActivityViewImpl.Companion.truncate
import org.slf4j.LoggerFactory

/**
 * Displays a single source row.
 */
class SourceRowView internal constructor(
        private val mainActivity: MainActivity,
        provider: SourceProvider<*>, root: ViewGroup
) {
    private val connection: SourceServiceConnection<*> = provider.connection
    private val mStatusIcon: View
    private val mBatteryLabel: ImageView
    private val sourceNameLabel: TextView
    private val devicePreferences: SharedPreferences
    private val filter = ChangeRunner("")
    private var sourceState: BaseSourceState? = null
    private var sourceName: String? = null
    private val batteryLevelCache = ChangeRunner<Float>()
    private val sourceNameCache = ChangeRunner<String>()
    private val statusCache = ChangeRunner<SourceStatusListener.Status>()

    private val splitRegex = this.mainActivity.getString(R.string.filter_split_regex).toRegex()

    init {
        devicePreferences = this.mainActivity.getSharedPreferences("device." + connection.serviceClassName, Context.MODE_PRIVATE)
        logger.info("Creating source row for provider {} and connection {}", provider, connection)
        val inflater = this.mainActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.activity_overview_source_row, root)

        (root.getChildAt(root.childCount - 1) as TableRow).apply {
            mStatusIcon = findViewById(R.id.status_icon)
            sourceNameLabel = findViewById(R.id.sourceNameLabel)
            mBatteryLabel = findViewById(R.id.batteryStatusLabel)
            findViewById<Button>(R.id.filterSourceButton).apply {
                if (provider.isFilterable) {
                    setOnClickListener { dialogFilterSource() }
                    isEnabled = true
                }
                text = provider.displayName
            }
            findViewById<View>(R.id.refreshButton)
                    .setOnClickListener { reconnectSource() }
        }
        setFilter(devicePreferences.getString("filter", "") ?: "")
    }

    private fun dialogFilterSource() {
        val input = EditText(mainActivity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(filter.value)
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
        filter.applyIfChanged(newValue) {
            devicePreferences.edit()
                    .putString("filter", it)
                    .apply()

            val allowed = it.split(splitRegex)

            logger.info("setting source filter {}", allowed)

            mainActivity.radarService?.setAllowedSourceIds(connection, allowed)
        }
    }

    private fun reconnectSource() {
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
        sourceState = connection.sourceState
        sourceName = when (sourceState?.status) {
                SourceStatusListener.Status.CONNECTED,
                SourceStatusListener.Status.CONNECTING -> connection.sourceName
                        ?.replace("Empatica", "")
                        ?.trim { c -> c <= ' ' }
                else -> null
        }
    }

    fun display() {
        updateBattery()
        updateSourceName()
        updateSourceStatus()
    }

    private fun updateSourceStatus() {
        statusCache.applyIfChanged(sourceState?.status ?: SourceStatusListener.Status.DISCONNECTED) { status ->
            logger.info("Source status is {}", status)

            mStatusIcon.setBackgroundResource(when(status) {
                SourceStatusListener.Status.CONNECTED -> R.drawable.status_connected
                SourceStatusListener.Status.DISCONNECTED -> R.drawable.status_disconnected
                SourceStatusListener.Status.READY -> R.drawable.status_searching
                SourceStatusListener.Status.CONNECTING -> R.drawable.status_searching
                else -> sourceStatusIconDefault
            })
        }
    }

    private fun updateBattery() {
        batteryLevelCache.applyIfChanged(sourceState?.batteryLevel ?: Float.NaN) {
            mBatteryLabel.setImageResource(when {
                it.isNaN() -> R.drawable.ic_battery_unknown
                it < 0.1 -> R.drawable.ic_battery_empty
                it < 0.3 -> R.drawable.ic_battery_low
                it < 0.6 -> R.drawable.ic_battery_50
                it < 0.85 -> R.drawable.ic_battery_80
                else -> R.drawable.ic_battery_full
            })
        }
    }

    private fun updateSourceName() {
        // \u2014 == —
        sourceNameCache.applyIfChanged(sourceName ?: "\u2014") {
            sourceNameLabel.text = it.truncate(MAX_UI_SOURCE_NAME_LENGTH)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SourceRowView::class.java)
        private const val MAX_UI_SOURCE_NAME_LENGTH = 10
        private const val sourceStatusIconDefault = R.drawable.status_searching
    }
}
