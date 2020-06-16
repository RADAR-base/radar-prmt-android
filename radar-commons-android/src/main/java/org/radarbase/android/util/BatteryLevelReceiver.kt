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

package org.radarbase.android.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BATTERY_CHANGED
import android.content.IntentFilter
import android.os.BatteryManager.*

/** Keep track of battery level events.  */
class BatteryLevelReceiver(
        private val context: Context,
        var listener: ((level: Float, isPlugged: Boolean) -> Unit)? = null) : SpecificReceiver {
    /** Latest battery level in range [0, 1].  */
    var level: Float = 1.0f
        private set
    /** Latest value whether the device is plugged into a charger.  */
    var isPlugged: Boolean = true
        private set

    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == ACTION_BATTERY_CHANGED) {
                val level1 = intent.getIntExtra(EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(EXTRA_SCALE, -1)

                level = level1 / scale.toFloat()
                isPlugged = intent.getIntExtra(EXTRA_PLUGGED, 0) > 0

                notifyListener()
            }
        }
    }

    override fun register() {
        val init = context.registerReceiver(receiver, IntentFilter(ACTION_BATTERY_CHANGED))
        receiver.onReceive(context, init)
    }

    override fun notifyListener() {
        listener?.let { it(level, isPlugged) }
    }

    override fun unregister() {
        context.unregisterReceiver(receiver)
    }
}
