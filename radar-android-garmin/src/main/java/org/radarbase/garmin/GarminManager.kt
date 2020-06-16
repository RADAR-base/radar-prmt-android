package org.radarbase.garmin

import android.util.Log
import com.garmin.device.realtime.*
import com.garmin.health.Device
import com.garmin.health.app
import org.radarbase.android.data.DataCache
import org.radarbase.android.source.AbstractSourceManager
import org.radarbase.android.source.SourceStatusListener
import org.radarbase.android.util.SafeHandler
import org.radarbase.garmin.interfaces.OnHealthDataReceieved
import org.radarbase.garmin.ui.realtime.RealTimeDataHandler
import org.radarcns.kafka.ObservationKey
import org.radarcns.passive.garmin.*
import android.app.Activity;
import com.garmin.health.ConnectionState


class GarminManager internal constructor(service: GarminService,private val handler: SafeHandler) : AbstractSourceManager<GarminService, GarminState>(service), OnHealthDataReceieved {
    private val stepsTopic: DataCache<ObservationKey, GarminGenericSteps> = createCache("android_garmin_generic_steps", GarminGenericSteps())
    private val heartRateVariabilityTopic: DataCache<ObservationKey, GarminGenericHeartRateVariability> = createCache("android_garmin_generic_heart_rate_variability", GarminGenericHeartRateVariability())
    private val stressTopic: DataCache<ObservationKey, GarminGenericStress> = createCache("android_garmin_generic_stress", GarminGenericStress())
    private val ascentTopic: DataCache<ObservationKey, GarminGenericAscent> = createCache("android_garmin_generic_ascent", GarminGenericAscent())
    private val spo2Topic: DataCache<ObservationKey, GarminGenericSpo2> = createCache("android_garmin_generic_spo2", GarminGenericSpo2())
    private val respirationTopic: DataCache<ObservationKey, GarminGenericRespiration> = createCache("android_garmin_generic_respiration", GarminGenericRespiration())
    private val heartRateTopic: DataCache<ObservationKey, GarminGenericHeartRate> = createCache("android_garmin_generic_heart_rate", GarminGenericHeartRate())
    private val intensityTopic: DataCache<ObservationKey, GarminGenericIntensity> = createCache("android_garmin_generic_intensity", GarminGenericIntensity())
    private val caloriesTopic: DataCache<ObservationKey, GarminGenericCalories> = createCache("android_garmin_generic_calories", GarminGenericCalories())
    private val accelerometerTopic: DataCache<ObservationKey, GarminGenericAccelerometer> = createCache("android_garmin_generic_accelerometer", GarminGenericAccelerometer())
    private val deviceInfoTopic: DataCache<ObservationKey, GarminGenericDeviceInfo> = createCache("android_garmin_generic_device_info", GarminGenericDeviceInfo())
    lateinit var apiKey: String


    override fun start(acceptableIds: Set<String>) {
        Log.w(TAG, "In Start")
        handler.start()
        handler.execute {
            RealTimeDataHandler.getInstance().registerListenerForHealthData(this)
            status = SourceStatusListener.Status.READY
        }

    }


    override fun intensityMinutesReceived(intensityMinutesResult: RealTimeIntensityMinutes?) {
        Log.i(TAG, intensityMinutesResult.toString())
        send(intensityTopic, GarminGenericIntensity(System.currentTimeMillis().toDouble(),intensityMinutesResult?.lastUpdated?.toDouble(),intensityMinutesResult?.totalDailyMinutes,
        intensityMinutesResult?.dailyModerateMinutes,intensityMinutesResult?.dailyVigorousMinutes,intensityMinutesResult?.totalWeeklyMinutes,intensityMinutesResult?.weeklyGoal))
    }

    override fun calorieRecieved(caloriesResult: RealTimeCalories?) {
        send(caloriesTopic,GarminGenericCalories(System.currentTimeMillis().toDouble(),caloriesResult?.lastUpdated?.toDouble(),caloriesResult?.currentActiveCalories,caloriesResult?.currentTotalCalories))
    }

    override fun spo2Received(spo2Result: RealTimeSpo2?) {
        send(spo2Topic,GarminGenericSpo2(System.currentTimeMillis().toDouble(),spo2Result?.readingTimestamp?.toDouble(),spo2Result?.spo2Reading))
    }

    override fun ascentDataReceived(ascentResult: RealTimeAscent?) {
        send(ascentTopic, GarminGenericAscent(System.currentTimeMillis().toDouble(),
                ascentResult?.lastUpdated?.toDouble(),
                ascentResult?.currentFloorsClimbed,
                ascentResult?.currentFloorsDescended,
                ascentResult?.currentMetersClimbed,
                ascentResult?.currentMetersDescended,
                ascentResult?.floorsClimbedGoal,
                ascentResult?.metersClimbedGoal
                ))
    }

    override fun accelerometerReceived(accelerometerResult: RealTimeAccelerometer?) {
        send(accelerometerTopic, GarminGenericAccelerometer(System.currentTimeMillis().toDouble(),
                accelerometerResult?.accelerometerSamples?.get(0)?.millisecondTimestamp?.toDouble(),
                accelerometerResult?.accelerometerSamples?.get(0)?.x?.toFloat(),
                accelerometerResult?.accelerometerSamples?.get(0)?.y?.toFloat(),
                accelerometerResult?.accelerometerSamples?.get(0)?.z?.toFloat(),
                accelerometerResult?.actualSamplingRate)
        )

    }

    override fun heartRateVariabilityReceived(heartRateVariabilityResult: RealTimeHeartRateVariability?) {
        send(heartRateVariabilityTopic, GarminGenericHeartRateVariability(System.currentTimeMillis().toDouble(),
                heartRateVariabilityResult?.lastUpdated?.toDouble(),
                heartRateVariabilityResult?.heartRateVariability))
    }

    override fun respirationReceived(respirationResult: RealTimeRespiration?) {
        Log.i(TAG, respirationResult.toString())
        send(respirationTopic, GarminGenericRespiration(System.currentTimeMillis().toDouble(),
                respirationResult?.lastUpdated?.toDouble(),
                respirationResult?.respirationRate
                ))
    }

    override fun heartRateReceived(heartRateResult: RealTimeHeartRate?) {
        val source: Source
        when(heartRateResult?.heartRateSource){
            RealTimeHeartRateSource.HR_STRAP -> source = Source.HR_STRAP
            RealTimeHeartRateSource.OHR_LOCKED -> source = Source.OHR_LOCKED
            RealTimeHeartRateSource.OHR_NO_LOCK -> source = Source.OHR_NO_LOCK
            RealTimeHeartRateSource.NO_SOURCE-> source = Source.HR_STRAP
            else -> source = Source.HR_STRAP
        }
        send(heartRateTopic, GarminGenericHeartRate(System.currentTimeMillis().toDouble(),
                heartRateResult?.lastUpdated?.toDouble(),
                heartRateResult?.currentHeartRate,
                heartRateResult?.currentRestingHeartRate,
                heartRateResult?.dailyHighHeartRate,
                heartRateResult?.dailyLowHeartRate,
                source))
    }

    override fun stressReceived(stressResult: RealTimeStress?) {
        send(stressTopic, GarminGenericStress(System.currentTimeMillis().toDouble(),
                stressResult?.lastUpdated?.toDouble(),stressResult?.stressScore))
    }

    override fun stepsReceived(steps: RealTimeSteps?) {
        send(stepsTopic,GarminGenericSteps(System.currentTimeMillis().toDouble(),steps?.lastUpdated?.toDouble(),
                steps?.currentStepCount,steps?.currentStepGoal))
    }

    override fun deviceInfoDetailsReceived(device: Device?) {
       val state: State;

        if (register(device?.address().toString(), device?.friendlyName().toString(), mapOf())) {
            Log.w(TAG, "Source Registered")
            // Set status to CONNECTED once the device is connected
            status = SourceStatusListener.Status.CONNECTED
        }
        when(device?.connectionState())
       {
           ConnectionState.CONNECTED -> state = State.CONNECTED
           ConnectionState.CONNECTING -> state = State.CONNECTING
           ConnectionState.DISCONNECTED -> state = State.DISCONNECTED
           else -> state = State.UNKNOWN
       }
        send(deviceInfoTopic, GarminGenericDeviceInfo(System.currentTimeMillis().toDouble(),System.currentTimeMillis().toDouble(),state,device?.model().toString(),device?.firmwareVersion(),device?.friendlyName()))
    }

    companion object {
        private const val TAG = "Garmin Manager"
    }

}