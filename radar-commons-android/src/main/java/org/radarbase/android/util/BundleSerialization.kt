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

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Parcel
import android.util.Base64
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException

object BundleSerialization {
    private val logger = LoggerFactory.getLogger(BundleSerialization::class.java)

    fun bundleToString(bundle: Bundle): String {
        return bundle.keySet()
                .joinToString(prefix = "{", postfix = "}") { "$it: ${bundle.get(it)}" }
    }

    fun getPersistentExtras(intent: Intent?, context: Context): Bundle? {
        val prefs = context.getSharedPreferences(context.javaClass.name, Context.MODE_PRIVATE)
        val bundle: Bundle? = intent?.extras?.also { saveToPreferences(prefs, it) }
                ?: restoreFromPreferences(prefs)

        return bundle?.apply {
            classLoader = BundleSerialization::class.java.classLoader
        }
    }

    fun saveToPreferences(prefs: SharedPreferences, `in`: Bundle) {
        val parcel = Parcel.obtain()
        val serialized: String? = try {
            `in`.writeToParcel(parcel, 0)

            ByteArrayOutputStream().use { bos ->
                bos.write(parcel.marshall())
                Base64.encodeToString(bos.toByteArray(), 0)
            }
        } catch (e: IOException) {
            logger.error("Failed to serialize bundle", e)
            null
        } finally {
            parcel.recycle()
        }
        serialized?.let {
            prefs.edit()
                    .putString("parcel", it)
                    .apply()
        }
    }

    fun restoreFromPreferences(prefs: SharedPreferences): Bundle? {
        return prefs.getString("parcel", null)?.let { serialized ->
            val data = Base64.decode(serialized, 0)
            val parcel = Parcel.obtain()
            try {
                parcel.run {
                    unmarshall(data, 0, data.size)
                    setDataPosition(0)
                    readBundle(prefs.javaClass.classLoader)
                }
            } finally {
                parcel.recycle()
            }
        }
    }
}
