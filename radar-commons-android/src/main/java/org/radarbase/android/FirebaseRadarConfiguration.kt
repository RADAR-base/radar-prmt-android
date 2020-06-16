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

package org.radarbase.android

import android.content.Context
import android.os.Handler
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import org.radarbase.android.RadarConfiguration.Companion.FIREBASE_FETCH_TIMEOUT_MS_KEY
import org.radarbase.android.RadarConfiguration.Companion.RADAR_CONFIGURATION_CHANGED
import org.radarbase.android.util.send
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("unused")
class FirebaseRadarConfiguration(context: Context, inDevelopmentMode: Boolean, defaultSettings: Int) : RadarConfiguration {
    private val firebase = FirebaseRemoteConfig.getInstance().apply {
        setDefaultsAsync(defaultSettings)
        isInDevelopmentMode = inDevelopmentMode
        fetch()
    }

    private val onFailureListener: OnFailureListener
    private val hasChange: AtomicBoolean = AtomicBoolean(false)
    override var status: RadarConfiguration.RemoteConfigStatus = RadarConfiguration.RemoteConfigStatus.INITIAL
        private set

    private val handler: Handler = Handler()
    private val onFetchCompleteHandler: OnCompleteListener<Void>
    private val localConfiguration: MutableMap<String, String> = ConcurrentHashMap()
    private val persistChanges: Runnable
    private var isInDevelopmentMode: Boolean = false
    private var firebaseKeys: Set<String> = HashSet(firebase.getKeysByPrefix(""))
    private val broadcaster = LocalBroadcastManager.getInstance(context)

    init {
        this.onFetchCompleteHandler = OnCompleteListener {  task ->
            if (task.isSuccessful) {
                status = RadarConfiguration.RemoteConfigStatus.FETCHED
                // Once the config is successfully fetched it must be
                // activated before newly fetched values are returned.
                activateFetched().addOnCompleteListener {
                    // Set global properties.
                    logger.info("RADAR configuration changed: {}", this@FirebaseRadarConfiguration)
                    broadcaster.send(RADAR_CONFIGURATION_CHANGED)
                }
            } else {
                status = RadarConfiguration.RemoteConfigStatus.ERROR
                logger.warn("Remote Config: Fetch failed. Stacktrace: {}", task.exception)
            }
        }

        this.onFailureListener = OnFailureListener {
            logger.info("Failed to fetch Firebase config")
            status = RadarConfiguration.RemoteConfigStatus.ERROR
            broadcaster.send(RADAR_CONFIGURATION_CHANGED)
        }

        val prefs = context.applicationContext.getSharedPreferences(javaClass.name, Context.MODE_PRIVATE)
        for ((key, value) in prefs.all) {
            if (value == null || value is String) {
                this.localConfiguration[key] = (value as String?)!!
            }
        }

        persistChanges = Runnable {
            val editor = prefs.edit()
            for ((key, value) in localConfiguration) {
                editor.putString(key, value)
            }
            editor.apply()

            if (hasChange.compareAndSet(true, false)) {
                logger.info("RADAR configuration changed: {}", this@FirebaseRadarConfiguration)
                broadcaster.send(RADAR_CONFIGURATION_CHANGED)            }
        }

        val googleApi = GoogleApiAvailability.getInstance()
        if (googleApi.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
            status = RadarConfiguration.RemoteConfigStatus.READY
            this.handler.postDelayed(object : Runnable {
                override fun run() {
                    fetch()
                    val delay = getLong(FIREBASE_FETCH_TIMEOUT_MS_KEY, FIREBASE_FETCH_TIMEOUT_MS_DEFAULT)
                    handler.postDelayed(this, delay)
                }
            }, getLong(FIREBASE_FETCH_TIMEOUT_MS_KEY, FIREBASE_FETCH_TIMEOUT_MS_DEFAULT))
        } else {
            status = RadarConfiguration.RemoteConfigStatus.UNAVAILABLE
        }
    }

    /**
     * Adds a new or updated setting to the local configuration. This will be persisted to
     * SharedPreferences. Using this will override Firebase settings. Setting it to `null`
     * means that the default value in code will be used, not the Firebase setting. Use
     * [.reset] to completely unset any local configuration.
     *
     * @param key configuration name
     * @param value configuration value
     * @return previous local value for given name, if any
     */
    override fun put(key: String, value: Any): String? {
        requireNotNull(value)
        require((value is String
                || value is Long
                || value is Int
                || value is Float
                || value is Boolean)) { ("Cannot put value of type " + value.javaClass
                + " into RadarConfiguration") }
        val config = value as? String ?: value.toString()
        val oldValue = getRawString(key)
        if (oldValue != config) {
            hasChange.set(true)
        }
        localConfiguration[key] = config
        return oldValue
    }

    override fun persistChanges() = persistChanges.run()

    /**
     * Reset configuration to Firebase Remote Config values. If no keys are given, all local
     * settings are reset, otherwise only the given keys are reset.
     * @param keys configuration names
     */
    override fun reset(vararg keys: String) {
        if (keys.isEmpty()) {
            localConfiguration.clear()
            hasChange.set(true)
        } else {
            for (key in keys) {
                val oldValue = getRawString(key)
                localConfiguration.remove(key)
                val newValue = getRawString(key)
                if (oldValue != newValue) {
                    hasChange.set(true)
                }
            }
        }
        persistChanges()
    }

    /**
     * Fetch the configuration from the firebase server.
     * @return fetch task or null status is [RadarConfiguration.RemoteConfigStatus.UNAVAILABLE].
     */
    override fun fetch(): Task<Void>? {
        val delay = if (isInDevelopmentMode) {
            0L
        } else {
            getLong(FIREBASE_FETCH_TIMEOUT_MS_KEY, FIREBASE_FETCH_TIMEOUT_MS_DEFAULT)
        }
        return fetch(delay)
    }

    /**
     * Fetch the configuration from the firebase server.
     * @param delay seconds
     * @return fetch task or null status is [RadarConfiguration.RemoteConfigStatus.UNAVAILABLE].
     */
    private fun fetch(delay: Long): Task<Void>? {
        if (status == RadarConfiguration.RemoteConfigStatus.UNAVAILABLE) {
            return null
        }
        val task = firebase.fetch(delay)
        synchronized(this) {
            status = RadarConfiguration.RemoteConfigStatus.FETCHING
            task.addOnCompleteListener(onFetchCompleteHandler)
            task.addOnFailureListener(onFailureListener)
        }
        return task
    }

    override fun forceFetch(): Task<Void>? = fetch(0L)

    override fun activateFetched(): Task<Boolean> {
        val result = firebase.activate()
        firebaseKeys = HashSet(firebase.getKeysByPrefix(""))
        return result
    }

    private fun getRawString(key: String): String? {
        return localConfiguration[key]
                ?: if (key in firebaseKeys) firebase.getValue(key).asString() else null
    }

    override fun optString(key: String): String? {
        val result = getRawString(key)
        return if (result?.isEmpty() == false) result else null
    }

    override fun containsKey(key: String) = key in firebase.getKeysByPrefix(key)

    override val keys: Set<String>
        get() {
            val baseKeys = HashSet(firebaseKeys + localConfiguration.keys)
            val iter = baseKeys.iterator()
            while (iter.hasNext()) {
                if (optString(iter.next()) == null) {
                    iter.remove()
                }
            }
            return baseKeys
        }

    override fun equals(other: Any?): Boolean {
        return (other != null
                && other.javaClass != javaClass
                && firebase == (other as FirebaseRadarConfiguration).firebase)
    }

    override fun hashCode(): Int = firebase.hashCode()

    override fun has(key: String) = key in localConfiguration
            || (key in firebaseKeys && firebase.getValue(key).asString().isNotEmpty())

    override fun toString(): String {
        val keys = keys
        val builder = StringBuilder(keys.size * 40 + 20)
        builder.append("RadarConfiguration:\n")
        for (key in keys) {
            builder.append("  ")
                    .append(key)
                    .append(": ")
                    .append(getString(key))
                    .append('\n')
        }
        return builder.toString()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FirebaseRadarConfiguration::class.java)
        private const val FIREBASE_FETCH_TIMEOUT_MS_DEFAULT = 12 * 60 * 60 * 1000L
    }
}
