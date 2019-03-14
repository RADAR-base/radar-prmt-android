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

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import org.radarbase.android.MainActivityView
import org.radarbase.android.device.DeviceServiceProvider
import org.radarcns.passive.ppg.PhonePpgProvider
import java.text.SimpleDateFormat
import java.util.*

class MainActivityViewImpl internal constructor(private val mainActivity: MainActivityImpl) : MainActivityView {
    private val rows = ArrayList<DeviceRowView>()
    private var savedConnections: List<DeviceServiceProvider<*>>? = null

    private var previousTimestamp: Long = 0
    @Volatile
    private var newServerStatus: String? = null

    // View elements
    private lateinit var mServerMessage: TextView
    private lateinit var mUserId: TextView
    private var userId: String? = null
    private var previousUserId: String? = null
    private lateinit var mProjectId: TextView
    private var projectId: String? = null
    private var previousProjectId: String? = null

    private val serverStatusMessage: String?
        get() {
            return mainActivity.radarService?.latestNumberOfRecordsSent?.let { numberOfRecords ->
                if (numberOfRecords.time >= 0 && previousTimestamp != numberOfRecords.time) {
                    previousTimestamp = numberOfRecords.time

                    val messageTimeStamp = timeFormat.format(numberOfRecords.time)

                    if (numberOfRecords.value < 0) {
                        String.format(Locale.US, "last upload failed at %1\$s", messageTimeStamp)
                    } else {
                        String.format(Locale.US, "last upload at %1\$s", messageTimeStamp)
                    }
                } else {
                    null
                }
            }
        }

    init {
        this.previousUserId = ""

        initializeViews()

        createRows()
    }

    private fun createRows() {
        if (mainActivity.radarService != null && mainActivity.radarService!!.connections != savedConnections) {
            val root = mainActivity.findViewById<ViewGroup>(R.id.deviceTable)
            while (root.childCount > 1) {
                root.removeView(root.getChildAt(1))
            }
            rows.clear()

            rows += mainActivity.radarService?.connections
                    ?.filter { it.isDisplayable }
                    ?.map { DeviceRowView(mainActivity, it, root) } ?: emptyList()

            savedConnections = mainActivity.radarService!!.connections
        }
    }

    override fun update() {
        createRows()
        for (row in rows) {
            row.update()
        }

        userId = mainActivity.userId
        projectId = mainActivity.projectId
        if (mainActivity.radarService != null) {
            newServerStatus = serverStatusMessage
        }
        mainActivity.runOnUiThread {
            rows.forEach(DeviceRowView::display)
            updateServerStatus()
            setUserId()
        }
    }

    private fun initializeViews() {
        mainActivity.apply {
            setContentView(R.layout.compact_overview)

            setSupportActionBar(findViewById<Toolbar>(R.id.toolbar).apply {
                setTitle(R.string.radar_prmt_title)
            })

            mServerMessage = findViewById(R.id.statusServerMessage)

            mUserId = findViewById(R.id.inputUserId)
            mProjectId = findViewById(R.id.inputProjectId)

            if (radarService?.connections?.any { it is PhonePpgProvider } == true) {
                findViewById<View>(R.id.startPpg)
                        .setOnClickListener { mainActivity.startPpgFragment() }
            } else {
                findViewById<View>(R.id.action_header).visibility = View.GONE
                findViewById<View>(R.id.action_divider).visibility = View.GONE
                findViewById<View>(R.id.startPpg).visibility = View.GONE
            }
        }
    }

    private fun updateServerStatus() {
        newServerStatus?.also {
            mServerMessage.text = it
        }
    }

    private fun setUserId() {
        if (userId != previousUserId) {
            mUserId.apply {
                if (userId == null) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    text = mainActivity.getString(R.string.user_id_message, truncate(userId, MAX_USERNAME_LENGTH))
                }

            }
            previousUserId = userId
        }
        if (projectId != previousProjectId) {
            mProjectId.apply {
                if (projectId == null) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    text = mainActivity.getString(R.string.study_id_message, truncate(projectId, MAX_USERNAME_LENGTH))
                }
            }
            previousProjectId = projectId
        }
    }

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        internal const val MAX_USERNAME_LENGTH = 20

        internal fun truncate(orig: String?, maxLength: Int): String {
            return when {
                orig == null -> ""
                orig.length > maxLength -> orig.substring(0, maxLength - 3) + "\u2026"
                else -> orig
            }
        }
    }
}
