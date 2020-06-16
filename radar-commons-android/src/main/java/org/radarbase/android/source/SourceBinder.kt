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

package org.radarbase.android.source

import android.os.Bundle
import org.radarbase.android.kafka.ServerStatusListener
import org.radarbase.data.RecordData
import java.io.IOException

interface SourceBinder<T : BaseSourceState> {
    /** Get the current source status  */
    val sourceState: T?
    /** Get the current source name, or null if unknown.  */
    val sourceName: String?
    /** Get the current server status  */
    val serverStatus: ServerStatusListener.Status
    /** Get the last number of records sent  */
    val serverRecordsSent: Map<String, Long>

    /** Start scanning and recording from a compatible source.
     * @param acceptableIds a set of source IDs that may be connected to.
     * If empty, no selection is made.
     */
    fun startRecording(acceptableIds: Set<String>)

    /** Stop scanning and recording  */
    fun stopRecording()

    @Throws(IOException::class)
    fun getRecords(topic: String, limit: Int): RecordData<Any, Any>?

    /** Update the configuration of the service  */
    fun updateConfiguration(bundle: Bundle)

    /** Number of records in cache unsent  */
    val numberOfRecords: Long?

    fun needsBluetooth(): Boolean

    fun shouldRemainInBackground(): Boolean
}
