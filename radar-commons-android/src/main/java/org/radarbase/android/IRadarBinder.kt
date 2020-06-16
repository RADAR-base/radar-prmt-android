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

package org.radarbase.android

import android.os.IBinder
import org.apache.avro.specific.SpecificRecord
import org.radarbase.android.data.DataHandler
import org.radarbase.android.kafka.ServerStatusListener
import org.radarbase.android.source.SourceProvider
import org.radarbase.android.source.SourceServiceConnection
import org.radarbase.android.util.TimedLong
import org.radarcns.kafka.ObservationKey

interface IRadarBinder : IBinder {
    val serverStatus: ServerStatusListener.Status

    val latestNumberOfRecordsSent: TimedLong

    val connections: List<SourceProvider<*>>

    val dataHandler: DataHandler<ObservationKey, SpecificRecord>?

    fun setAllowedSourceIds(connection: SourceServiceConnection<*>, allowedIds: Collection<String>)

    fun startScanning()
    fun stopScanning()

    fun needsBluetooth(): Boolean
}
