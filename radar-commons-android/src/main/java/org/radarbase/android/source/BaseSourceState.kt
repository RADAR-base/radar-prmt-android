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

import org.radarcns.kafka.ObservationKey

/** Current state of a source.  */
open class BaseSourceState {
    val id = ObservationKey(null, null, null)

    @get:Synchronized
    @set:Synchronized
    open var status: SourceStatusListener.Status = SourceStatusListener.Status.DISCONNECTED

    /**
     * Get the battery level, between 0 (empty) and 1 (full).
     * @return battery level or Float.NaN if unknown.
     */
    open val batteryLevel: Float get() = java.lang.Float.NaN

    /**
     * Get the temperature in degrees Celcius.
     * @return temperature or Float.NaN if unknown.
     */
    open val temperature: Float get() = java.lang.Float.NaN

    /**
     * Get the heart rate in bpm.
     * @return heart rate or Float.NaN if unknown.
     */
    open val heartRate: Float get() = java.lang.Float.NaN

    /**
     * Get the x, y and z components of the acceleration in g.
     * @return array of acceleration or of Float.NaN if unknown
     */
    open val acceleration: FloatArray
        get() = floatArrayOf(java.lang.Float.NaN, java.lang.Float.NaN, java.lang.Float.NaN)

    /**
     * Get the magnitude of the acceleration in g, computed from [.getAcceleration].
     * @return acceleration or Float.NaN if unknown.
     */
    open val accelerationMagnitude: Float
        get() {
            val acceleration = acceleration
            return Math.sqrt(
                    (acceleration[0] * acceleration[0]
                            + acceleration[1] * acceleration[1]
                            + acceleration[2] * acceleration[2]).toDouble()).toFloat()
        }

    /**
     * Whether the state will gather any temperature information. This implementation returns false.
     */
    open val hasTemperature: Boolean
        get() = false

    /**
     * Whether the state will gather any heart rate information. This implementation returns false.
     */
    open val hasHeartRate: Boolean
        get() = false

    /**
     * Whether the state will gather any acceleration information. This implementation returns false.
     */
    open val hasAcceleration: Boolean
        get() = false
}
