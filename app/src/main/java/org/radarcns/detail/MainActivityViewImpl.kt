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

import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.Toolbar
import org.radarbase.android.IRadarBinder
import org.radarbase.android.MainActivityView
import org.radarbase.android.source.SourceProvider
import org.radarbase.android.util.ChangeApplier
import org.radarbase.android.util.ChangeRunner
import org.radarbase.android.util.TimedLong
import org.radarbase.android.widget.repeatAnimation
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivityViewImpl(
    private val mainActivity: MainActivityImpl
) : MainActivityView {
    private val connectionRows = ChangeRunner<List<SourceProvider<*>>>(emptyList())
    private val actionsCells = ChangeRunner<List<SourceProvider.Action>>(emptyList())

    private val timestampCache = ChangeApplier<TimedLong, String>({ numberOfRecords ->
        val msg = if (numberOfRecords.value >= 0) R.string.last_upload_succeeded
        else R.string.last_upload_failed
        mainActivity.getString(msg, timeFormat.format(numberOfRecords.time))
    }, { b -> time == b?.time })
    private val serverStatusCache = ChangeRunner<String>()

    // View elements
    private val mServerMessage: TextView
    private val mUserId: TextView
    private val mSourcesTable: ViewGroup
    private val mProjectId: TextView
    private val mActionLayout: GridLayout
    private val mActionWrapperLayout: LinearLayout
    private val mDevicesNoneText: View

    private val userIdCache = ChangeRunner<String>()
    private val projectIdCache = ChangeRunner<String>()
    private var rows: List<SourceRowView> = emptyList()

    private val serverStatusMessage: String?
        get() {
            return mainActivity.radarService?.latestNumberOfRecordsSent?.let { numberOfRecords ->
                if (numberOfRecords.time >= 0) {
                    timestampCache.applyIfChanged(numberOfRecords)
                } else {
                    null
                }
            }
        }

    init {
        logger.debug("Creating main activity view")
        mainActivity.apply {
            setContentView(R.layout.compact_overview)

            setSupportActionBar(findViewById<Toolbar>(R.id.toolbar).apply {
                setTitle(R.string.radar_prmt_title)
            })

            mServerMessage = findViewById(R.id.statusServerMessage)

            mUserId = findViewById(R.id.inputUserId)
            mProjectId = findViewById(R.id.inputProjectId)
            mSourcesTable = findViewById(R.id.sourcesTable)

            mActionLayout = findViewById(R.id.actionLayout)
            mActionWrapperLayout = findViewById(R.id.actionWrapperLayout)

            mDevicesNoneText = findViewById(R.id.no_devices)

            findViewById<ImageView>(R.id.logo).repeatAnimation()
            this@MainActivityViewImpl.update()
        }
    }

    override fun onRadarServiceBound(binder: IRadarBinder) {
        logger.debug("Radar service bound")
    }

    override fun update() {
        val providers = mainActivity.radarService
                ?.connections
                ?.filter { it.isDisplayable }
                ?: emptyList()

        val currentActions = mainActivity.radarService
                ?.connections
                ?.flatMap { it.actions }
                ?: emptyList()

        rows.forEach(SourceRowView::update)

        mainActivity.runOnUiThread {
            connectionRows.applyIfChanged(providers) { p ->
                val root = mSourcesTable.apply {
                    while (childCount > 1) {
                        removeView(getChildAt(1))
                    }
                }

                if (p.isEmpty()) {
                    mDevicesNoneText.visibility = View.VISIBLE
                    mSourcesTable.visibility = View.GONE
                    rows = listOf()
                } else {
                    mDevicesNoneText.visibility = View.GONE
                    mSourcesTable.visibility = View.VISIBLE
                    rows = p.map {
                        SourceRowView(mainActivity, it, root).apply {
                            update()
                        }
                    }
                }
            }
            actionsCells.applyIfChanged(currentActions) { actionList ->
                if (actionList.isNotEmpty()) {
                    mActionWrapperLayout.visibility = View.VISIBLE
                    mActionLayout.apply {
                        visibility = View.VISIBLE
                        removeAllViews()
                        actionList.forEach { action ->
                            addView(Button(mainActivity).apply {
                                text = action.name
                                setOnClickListener { mainActivity.apply(action.activate) }
                            })
                        }
                    }
                } else {
                    mActionWrapperLayout.visibility = View.GONE
                    mActionLayout.apply {
                        visibility = View.GONE
                        removeAllViews()
                    }
                }
            }

            rows.forEach(SourceRowView::display)
            updateServerStatus()
            setUserId()
        }
    }

    private fun updateServerStatus() {
        serverStatusCache.applyIfChanged(serverStatusMessage ?: "\u2014") {
            mServerMessage.text = it
        }
    }

    private fun setUserId() {
        userIdCache.applyIfChanged(mainActivity.userId ?: "") { id ->
            mUserId.apply {
                if (id.isEmpty()) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    text = id
                }

            }
        }
        projectIdCache.applyIfChanged(mainActivity.projectId ?: "") { id ->
            mProjectId.apply {
                if (id.isEmpty()) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    text = id
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MainActivityViewImpl::class.java)
        private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}
