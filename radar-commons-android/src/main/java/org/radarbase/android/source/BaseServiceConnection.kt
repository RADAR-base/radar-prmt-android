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

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
//import com.crashlytics.android.Crashlytics
import org.radarbase.android.kafka.ServerStatusListener
import org.radarbase.data.RecordData
import org.radarbase.util.Strings
import org.slf4j.LoggerFactory
import java.io.IOException

open class BaseServiceConnection<S : BaseSourceState>(private val serviceClassName: String) : ServiceConnection {
    @get:Synchronized
    var sourceStatus: SourceStatusListener.Status? = null
        protected set

    @get:Synchronized
    protected var serviceBinder: SourceBinder<S>? = null
        private set

    val sourceName: String?
        get() = serviceBinder?.sourceName

    val isRecording: Boolean
        get() = sourceStatus != SourceStatusListener.Status.DISCONNECTED

    val serverStatus: ServerStatusListener.Status?
        get() = serviceBinder?.serverStatus

    val serverSent: Map<String, Long>?
        get() = serviceBinder?.serverRecordsSent

    val sourceState: S?
        get() = serviceBinder?.sourceState

    init {
        this.serviceBinder = null
        this.sourceStatus = SourceStatusListener.Status.DISCONNECTED
    }

    override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
        if (serviceBinder == null && service != null) {
            logger.debug("Bound to service {}", className)
            try {
                @Suppress("UNCHECKED_CAST")
                serviceBinder = service as SourceBinder<S>
                sourceStatus = sourceState?.status
            } catch (ex: ClassCastException) {
                //Crashlytics.logException(IllegalStateException("Cannot process remote source services.", ex))
            }
        } else {
            logger.info("Trying to re-bind service, from {} to {}", serviceBinder, service)
        }
    }

    @Throws(IOException::class)
    fun getRecords(topic: String, limit: Int): RecordData<Any, Any>? {
        return serviceBinder?.getRecords(topic, limit)
    }

    /**
     * Start looking for sources to record.
     * @param acceptableIds case insensitive parts of source ID's that are allowed to connect.
     */
    fun startRecording(acceptableIds: Set<String>) {
        try {
            serviceBinder?.startRecording(acceptableIds)
            sourceStatus = serviceBinder?.sourceState?.status
        } catch (ex: IllegalStateException) {
            logger.error("Cannot start service {}: {}", this, ex.message)
        }
    }

    fun stopRecording() {
        serviceBinder?.stopRecording()
    }

    fun hasService(): Boolean {
        return serviceBinder != null
    }

    override fun onServiceDisconnected(className: ComponentName?) {
        // only do these steps once
        if (hasService()) {
            synchronized(this) {
                serviceBinder = null
                sourceStatus = SourceStatusListener.Status.DISCONNECTED
            }
        }
    }

    fun updateConfiguration(bundle: Bundle) {
        serviceBinder?.updateConfiguration(bundle)
    }

    fun numberOfRecords(): Long? {
        return serviceBinder?.numberOfRecords
    }

    /**
     * True if given string is a substring of the source name.
     */
    fun isAllowedSource(values: Collection<String>): Boolean {
        if (values.isEmpty()) {
            return true
        }
        val idOptions = listOfNotNull(
                serviceBinder?.sourceName,
                serviceBinder?.sourceState?.id?.getSourceId())

        return !idOptions.isEmpty() && values
                .map(Strings::containsIgnoreCasePattern)
                .any { pattern -> idOptions.any { pattern.matcher(it).find() } }
    }

    fun needsBluetooth(): Boolean {
        return serviceBinder?.needsBluetooth() == true
    }

    fun mayBeDisabledInBackground(): Boolean {
        return serviceBinder?.shouldRemainInBackground() == false
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val otherService = other as BaseServiceConnection<*>
        return this.serviceClassName == otherService.serviceClassName
    }

    override fun hashCode(): Int {
        return serviceClassName.hashCode()
    }

    override fun toString(): String {
        return "ServiceConnection<$serviceClassName>"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BaseServiceConnection::class.java)
    }
}
