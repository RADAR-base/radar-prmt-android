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

import android.content.Intent
import android.view.View

import org.radarbase.android.MainActivity
import org.radarbase.android.MainActivityView
import org.radarcns.passive.ppg.PhonePpgActivity

class MainActivityImpl : MainActivity() {
    override fun createView(): MainActivityView {
        return MainActivityViewImpl(this)
    }

    fun logout(view: View) {
        logout(true)
    }

    fun showInfo(view: View) {
        startActivity(Intent(this, InfoActivity::class.java))
    }

    fun showSettings(view: View) {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    fun startPpgFragment() {
        startActivity(Intent(this, PhonePpgActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        })
        overridePendingTransition(0, 0)
        finish()
    }
}
