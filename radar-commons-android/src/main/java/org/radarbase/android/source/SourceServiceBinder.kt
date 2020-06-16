package org.radarbase.android.source

import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import org.radarbase.android.kafka.ServerStatusListener
import org.radarbase.data.RecordData
import java.io.IOException

class SourceServiceBinder<T : BaseSourceState>(private val sourceService: SourceService<T>) : Binder(), SourceBinder<T> {
    @Throws(IOException::class)
    override fun getRecords(topic: String, limit: Int): RecordData<Any, Any>? {
        val localDataHandler = sourceService.dataHandler ?: return null
        return localDataHandler.getCache(topic).getRecords(limit)
    }

    override val sourceState: T
        get() = sourceService.state

    override val sourceName: String?
        get() = sourceService.sourceManager?.name

    override fun startRecording(acceptableIds: Set<String>) {
        sourceService.startRecording(acceptableIds)
    }

    override fun stopRecording() {
        sourceService.stopRecording()
    }

    override val serverStatus: ServerStatusListener.Status
        get() = sourceService.dataHandler?.status ?: ServerStatusListener.Status.DISCONNECTED

    override val serverRecordsSent: Map<String, Long>
        get() = sourceService.dataHandler?.recordsSent ?: mapOf()

    override fun updateConfiguration(bundle: Bundle) {
        sourceService.onInvocation(bundle)
    }

    override val numberOfRecords: Long?
        get() = sourceService.dataHandler?.let { data ->
            data.caches
                    .map { it.numberOfRecords }
                    .reduce { acc, num -> acc + num }
        }

    override fun needsBluetooth(): Boolean {
        return sourceService.isBluetoothConnectionRequired
    }

    override fun shouldRemainInBackground(): Boolean {
        return sourceService.state.status != SourceStatusListener.Status.DISCONNECTED
    }

    public override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        throw UnsupportedOperationException()
    }
}
