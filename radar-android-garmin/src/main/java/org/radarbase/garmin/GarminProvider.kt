package org.radarbase.garmin
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import org.radarbase.android.RadarService
import org.radarbase.android.source.SourceProvider
import org.radarbase.garmin.ui.MainActivity


class GarminProvider(radarService: RadarService) : SourceProvider<GarminState>(radarService) {
    override val permissionsNeeded = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    override val featuresNeeded = listOf(PackageManager.FEATURE_BLUETOOTH, PackageManager.FEATURE_BLUETOOTH_LE)
    override val pluginNames: List<String>
        get() = listOf( "garmin",
                ".garmin.GarminProvider",
                "org.radarbase.garmin.GarminProvider",
                "org.radarcns.garmin.GarminProvider")
    override val serviceClass: Class<GarminService> = GarminService::class.java
    override val sourceProducer: String = "Garmin"

    override val sourceModel: String = "Generic"

    override val version = "1.0.0"

    override val displayName: String
        get() = radarService.getString(R.string.garminlabel)

    override val actions: List<Action>
        get() = listOf(Action(radarService.getString(R.string.pair)) {
            startActivity(Intent(this, MainActivity::class.java))
        })
}