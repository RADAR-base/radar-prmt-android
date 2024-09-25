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

package org.radarcns.detail

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import org.radarbase.android.MainActivity
import org.radarbase.android.MainActivityView
import org.radarbase.android.RadarApplication.Companion.radarApp

class MainActivityImpl : MainActivity() {

//    private var mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        radarApp.notificationHandler.cancel(MainActivityBootStarter.BOOT_START_NOTIFICATION_ID)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun createView(): MainActivityView {
        return MainActivityViewImpl(this)
    }



    fun logout(@Suppress("UNUSED_PARAMETER")item: MenuItem) {
        logout(true)

        /* Not starting the splash activity again, login activity automatically starts when main activity is closed

        val intent = Intent(this, SplashActivityImpl::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        mainHandler.postDelayed({
            startActivity(intent)
        }, 1000)
        finish()
        */
    }

    fun showSettings(@Suppress("UNUSED_PARAMETER")item: MenuItem) {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    fun showInfo(@Suppress("UNUSED_PARAMETER") item: MenuItem) {
        startActivity(Intent(this, InfoActivity::class.java))
    }
}
