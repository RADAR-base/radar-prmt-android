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

package org.radarbase.android.auth

import android.app.Activity
import android.content.Intent
import com.google.zxing.client.android.Intents.Scan.MODE
import com.google.zxing.client.android.Intents.Scan.QR_CODE_MODE
import com.google.zxing.integration.android.IntentIntegrator

/**
 * QR code scanner.
 * @param callback result contents callback. Gets a String result if a QR code was scanned,
 *                 `null` otherwise.
 * @param activity to call back to when scanning has finished.
 */
class QrCodeScanner(private val activity: Activity, private val callback: (String?) -> Unit) {
    /** Start scanning for a QR code. */
    fun start() {
        IntentIntegrator(activity).apply {
            addExtra(MODE, QR_CODE_MODE)
            initiateScan()
        }
    }

    /**
     * Call when onActivityResult of the given activity is called. Only call this if
     * the intent is not null.
     * @param requestCode request code provided to [Activity.onActivityResult]
     * @param resultCode result code provided to [Activity.onActivityResult]
     * @param data Intent provided to [Activity.onActivityResult]
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        callback(IntentIntegrator.parseActivityResult(requestCode, resultCode, data)?.contents)
    }
}
