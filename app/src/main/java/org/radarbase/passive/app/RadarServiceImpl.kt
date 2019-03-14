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

package org.radarbase.passive.app

import android.Manifest.permission.RECEIVE_BOOT_COMPLETED
import android.os.Build
import org.radarbase.android.RadarService
import java.util.*

class RadarServiceImpl : RadarService() {
    override val servicePermissions: List<String>
        get() {
            return ArrayList(super.servicePermissions).also {
                it += RECEIVE_BOOT_COMPLETED
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    it += REQUEST_IGNORE_BATTERY_OPTIMIZATIONS_COMPAT
                }
            }
        }

    override fun doConfigure() {
        super.doConfigure()
        configureRunAtBoot(MainActivityBootStarter::class.java)
    }
}
