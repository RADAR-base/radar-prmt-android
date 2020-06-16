package org.radarbase.garmin
import android.os.Process
import org.radarbase.android.source.SourceService
import org.radarbase.android.util.SafeHandler
class GarminService : SourceService<GarminState>() {
    override val defaultState: GarminState
        get() = GarminState()
    private lateinit var handler: SafeHandler

    override fun onCreate() {
        super.onCreate()
        handler = SafeHandler.getInstance("Garmin-safe-handler", Process.THREAD_PRIORITY_MORE_FAVORABLE)
    }

    override fun createSourceManager() = GarminManager(this,handler)

}