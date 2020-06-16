package org.radarbase.android.util

import android.content.Context

class BatteryStageReceiver(context: Context, stageLevels: StageLevels = StageLevels(0.1f, 0.3f),
                           private val listener: (BatteryStage) -> Unit): SpecificReceiver {
    var stage: BatteryStage = BatteryStage.FULL
        private set

    private val batteryLevelReceiver = BatteryLevelReceiver(context) { level, isPlugged ->
        stage = when {
            isPlugged -> BatteryStage.FULL
            level <= _stageLevels.value.minimum -> BatteryStage.EMPTY
            level <= _stageLevels.value.reduced -> BatteryStage.REDUCED
            else -> BatteryStage.FULL
        }
        listener(stage)
    }

    private val _stageLevels = ChangeRunner(stageLevels)

    var stageLevels: StageLevels
        get() = _stageLevels.value.copy()
        set(value) {
            _stageLevels.applyIfChanged(value.copy()) { notifyListener() }
        }

    init {
        notifyListener()
    }

    enum class BatteryStage {
        FULL, EMPTY, REDUCED
    }

    override fun register() = batteryLevelReceiver.register()

    override fun notifyListener() = batteryLevelReceiver.notifyListener()

    override fun unregister() = batteryLevelReceiver.unregister()

    data class StageLevels(var minimum: Float, var reduced: Float)
}
