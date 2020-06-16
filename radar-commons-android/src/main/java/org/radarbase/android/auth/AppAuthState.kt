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

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import androidx.annotation.Keep
import okhttp3.Headers
import org.json.JSONArray
import org.json.JSONException
import org.radarbase.android.auth.LoginManager.Companion.AUTH_TYPE_UNKNOWN
import org.radarbase.android.auth.portal.ManagementPortalClient.Companion.SOURCES_PROPERTY
import org.radarcns.android.auth.AppSource
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.Serializable
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap

/** Authentication state of the application.  */
@Keep
@Suppress("unused")
class AppAuthState private constructor(builder: Builder) {
    val projectId: String?
    val userId: String?
    val token: String?
    val tokenType: Int
    val authenticationSource: String?
    val needsRegisteredSources: Boolean
    private val expiration: Long
    val lastUpdate: Long
    val attributes: Map<String, String>
    val headers: List<Map.Entry<String, String>>
    val sourceMetadata: List<SourceMetadata>
    val sourceTypes: List<SourceType>
    val isPrivacyPolicyAccepted: Boolean
    val okHttpHeaders: Headers

    val isValid: Boolean
        get() = isPrivacyPolicyAccepted && expiration > System.currentTimeMillis()

    val isInvalidated: Boolean
        get() = expiration == 0L

    constructor() : this(Builder())

    constructor(initializer: Builder.() -> Unit) : this(Builder().also(initializer))

    init {
        this.projectId = builder.projectId
        this.userId = builder.userId
        this.token = builder.token
        this.tokenType = builder.tokenType
        this.expiration = builder.expiration
        this.attributes = HashMap(builder.attributes)
        this.sourceTypes = ArrayList(builder.sourceTypes)
        this.sourceMetadata = ArrayList(builder.sourceMetadata)
        this.headers = ArrayList(builder.headers)
        this.lastUpdate = builder.lastUpdate
        this.isPrivacyPolicyAccepted = builder.isPrivacyPolicyAccepted
        this.authenticationSource = builder.authenticationSource
        this.needsRegisteredSources = builder.needsRegisteredSources
        this.okHttpHeaders = Headers.Builder().apply {
            for (header in headers) {
                add(header.key, header.value)
            }
        }.build()
    }

    fun getAttribute(key: String) = attributes[key]

    private fun serializableAttributeList() = serializedMap(attributes.entries)

    private fun serializableHeaderList() = serializedMap(headers)

    fun isValidFor(time: Long, unit: TimeUnit) = isPrivacyPolicyAccepted
            && expiration - unit.toMillis(time) > System.currentTimeMillis()

    val timeSinceLastUpdate: Long
            get() = SystemClock.elapsedRealtime() - lastUpdate

    fun addToPreferences(context: Context) {
        val prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)

        prefs.edit().apply {
            putString(LOGIN_PROJECT_ID, projectId)
            putString(LOGIN_USER_ID, userId)
            putString(LOGIN_TOKEN, token)
            putString(LOGIN_HEADERS_LIST, serializableHeaderList())
            putString(LOGIN_ATTRIBUTES, serializableAttributeList())
            putInt(LOGIN_TOKEN_TYPE, tokenType)
            putLong(LOGIN_EXPIRATION, expiration)
            putBoolean(LOGIN_PRIVACY_POLICY_ACCEPTED, isPrivacyPolicyAccepted)
            putString(LOGIN_AUTHENTICATION_SOURCE, authenticationSource)
            putBoolean(LOGIN_NEEDS_REGISTERD_SOURCES, needsRegisteredSources)
            remove(LOGIN_PROPERTIES)
            remove(LOGIN_HEADERS)
            putStringSet(LOGIN_APP_SOURCES_LIST, HashSet<String>().apply {
                addAll(sourceMetadata.map(SourceMetadata::toJsonString))
            })
            putStringSet(LOGIN_SOURCE_TYPES, HashSet<String>().apply {
                addAll(sourceTypes.map(SourceType::toJsonString))
            })
            remove(SOURCES_PROPERTY)
        }.apply()
    }

    fun alter(changes: Builder.() -> Unit): AppAuthState {
        return Builder().also {
            it.projectId = projectId
            it.userId = userId
            it.token = token
            it.tokenType = tokenType
            it.expiration = expiration
            it.authenticationSource = authenticationSource
            it.isPrivacyPolicyAccepted = isPrivacyPolicyAccepted

            it.attributes += attributes
            it.sourceMetadata += sourceMetadata
            it.sourceTypes += sourceTypes
            it.headers += headers
        }.apply(changes).build()
    }

    class Builder {
        val lastUpdate = SystemClock.elapsedRealtime()

        val headers: MutableCollection<Map.Entry<String, String>> = mutableListOf()
        val sourceMetadata: MutableCollection<SourceMetadata> = mutableListOf()
        val attributes: MutableMap<String, String> = mutableMapOf()

        var needsRegisteredSources = true

        var projectId: String? = null
        var userId: String? = null
        var token: String? = null
        var authenticationSource: String? = null
        var tokenType = AUTH_TYPE_UNKNOWN
        var expiration: Long = 0
        var isPrivacyPolicyAccepted = false
        val sourceTypes: MutableCollection<SourceType> = mutableListOf()

        @Deprecated("Use safe attributes instead of properties", replaceWith = ReplaceWith("attributes.addAll(properties)"))
        fun properties(properties: Map<String, Serializable>?): Builder {
            if (properties != null) {
                for ((key, value) in properties) {
                    @Suppress("UNCHECKED_CAST", "deprecation")
                    when {
                        key == SOURCES_PROPERTY -> appSources(value as List<AppSource>)
                        value is String -> this.attributes[key] = value
                        else -> logger.warn("Property {} no longer mapped in AppAuthState. Value discarded: {}", key, value)
                    }
                }
            }
            return this
        }

        fun parseAttributes(jsonString: String?): Builder = apply {
            jsonString?.also {
                attributes += try {
                    deserializedMap(it)
                } catch (e: JSONException) {
                    logger.warn("Cannot deserialize AppAuthState attributes: {}", e.toString())
                    emptyMap<String, String>()
                }
            }
        }

        fun invalidate(): Builder = apply { expiration = 0L }

        fun setHeader(name: String, value: String): Builder = apply {
            headers -= headers.filter { it.key == name }
            addHeader(name, value)
        }

        fun addHeader(name: String, value: String): Builder = apply {
            headers += AbstractMap.SimpleImmutableEntry(name, value)
        }

        fun parseHeaders(jsonString: String?): Builder = apply {
            jsonString?.also {
                this.headers += try {
                    deserializedEntryList(it)
                } catch (e: JSONException) {
                    logger.warn("Cannot deserialize AppAuthState attributes: {}", e.toString())
                    emptyList<Map.Entry<String, String>>()
                }
            }
        }

        @Deprecated("Use safe sourceMetadata instead of appSources", replaceWith = ReplaceWith("sourceMetadata.addAll(appSources)"))
        @Suppress("deprecation")
        private fun appSources(appSources: List<AppSource>?): Builder = apply {
            appSources?.also { sources ->
                sourceMetadata += sources.map { SourceMetadata(it) }
            }
        }

        @Throws(JSONException::class)
        fun parseSourceTypes(sourceJson: Collection<String>?): Builder = apply {
            sourceJson?.also { types ->
                sourceTypes += types.map { SourceType(it) }
            }
        }

        @Throws(JSONException::class)
        fun parseSourceMetadata(sourceJson: Collection<String>?): Builder = apply {
            sourceJson?.also { sources ->
                sourceMetadata += sources.map { SourceMetadata(it) }
            }
        }

        fun build(): AppAuthState {
            sourceMetadata.forEach { it.deduplicateType(sourceTypes) }
            return AppAuthState(this)
        }
    }

    override fun toString(): String {
        return ("AppAuthState{"
                + "authenticationSource='" + authenticationSource + '\''.toString()
                + ", \nprojectId='" + projectId + '\''.toString() +
                ", \nuserId='" + userId + '\''.toString() +
                ", \ntoken='" + token + '\''.toString() +
                ", \ntokenType=" + tokenType +
                ", \nexpiration=" + expiration +
                ", \nlastUpdate=" + lastUpdate +
                ", \nattributes=" + attributes +
                ", \nsourceMetadata=" + sourceMetadata +
                ", \nparseHeaders=" + headers +
                ", \nisPrivacyPolicyAccepted=" + isPrivacyPolicyAccepted +
                "\n")
    }

    companion object {
        private const val LOGIN_PROJECT_ID = "org.radarcns.android.auth.AppAuthState.projectId"
        private const val LOGIN_USER_ID = "org.radarcns.android.auth.AppAuthState.userId"
        private const val LOGIN_TOKEN = "org.radarcns.android.auth.AppAuthState.token"
        private const val LOGIN_TOKEN_TYPE = "org.radarcns.android.auth.AppAuthState.tokenType"
        private const val LOGIN_EXPIRATION = "org.radarcns.android.auth.AppAuthState.expiration"
        private const val AUTH_PREFS = "org.radarcns.auth"
        private const val LOGIN_PROPERTIES = "org.radarcns.android.auth.AppAuthState.properties"
        private const val LOGIN_ATTRIBUTES = "org.radarcns.android.auth.AppAuthState.attributes"
        private const val LOGIN_HEADERS = "org.radarcns.android.auth.AppAuthState.parseHeaders"
        private const val LOGIN_HEADERS_LIST = "org.radarcns.android.auth.AppAuthState.headerList"
        private const val LOGIN_APP_SOURCES_LIST = "org.radarcns.android.auth.AppAuthState.appSourcesList"
        private const val LOGIN_PRIVACY_POLICY_ACCEPTED = "org.radarcns.android.auth.AppAuthState.isPrivacyPolicyAccepted"
        private const val LOGIN_AUTHENTICATION_SOURCE = "org.radarcns.android.auth.AppAuthState.authenticationSource"
        private const val LOGIN_NEEDS_REGISTERD_SOURCES = "org.radarcns.android.auth.AppAuthState.needsRegisteredSources"
        private const val LOGIN_SOURCE_TYPES = "org.radarcns.android.auth.AppAuthState.sourceTypes"

        private val logger = LoggerFactory.getLogger(AppAuthState::class.java)

        private fun serializedMap(map: Collection<Map.Entry<String, String>>): String {
            val array = JSONArray()
            for (entry in map) {
                array.put(entry.key)
                array.put(entry.value)
            }
            return array.toString()
        }

        @Throws(JSONException::class)
        private fun deserializedMap(jsonString: String): Map<String, String> {
            val array = JSONArray(jsonString)
            val map = HashMap<String, String>(array.length() * 4 / 6 + 1)
            var i = 0
            while (i < array.length()) {
                map[array.getString(i)] = array.getString(i + 1)
                i += 2
            }
            return map
        }

        @Throws(JSONException::class)
        private fun deserializedEntryList(jsonString: String): List<Map.Entry<String, String>> {
            val array = JSONArray(jsonString)
            val list = ArrayList<Map.Entry<String, String>>(array.length() / 2)
            var i = 0
            while (i < array.length()) {
                list += AbstractMap.SimpleImmutableEntry(array.getString(i), array.getString(i + 1))
                i += 2
            }
            return list
        }

        private fun readSerializable(prefs: SharedPreferences, key: String): Any? {
            val propString = prefs.getString(key, null)
            if (propString != null) {
                val propBytes = Base64.decode(propString, Base64.NO_WRAP)
                try {
                    ByteArrayInputStream(propBytes).use { bi ->
                        ObjectInputStream(bi).use { it.readObject() }
                    }
                } catch (ex: IOException) {
                    logger.warn("Failed to deserialize object {} from preferences", key, ex)
                } catch (ex: ClassNotFoundException) {
                    logger.warn("Failed to deserialize object {} from preferences", key, ex)
                }

            }
            return null
        }

        fun from(context: Context): AppAuthState {
            val prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)

            val builder = Builder()

            try {
                readSerializable(prefs, LOGIN_PROPERTIES)
                        ?.let {
                            @Suppress("UNCHECKED_CAST")
                            it as HashMap<String, out Serializable>?
                        }?.also {
                            @Suppress("DEPRECATION")
                            builder.properties(it)
                        }
            } catch (ex: Exception) {
                logger.warn("Cannot read AppAuthState properties", ex)
            }

            try {
                readSerializable(prefs, LOGIN_HEADERS)
                        ?.let {
                            @Suppress("UNCHECKED_CAST")
                            it as ArrayList<Map.Entry<String, String>>?
                        }
                        ?.also { builder.headers += it }
            } catch (ex: Exception) {
                logger.warn("Cannot read AppAuthState parseHeaders", ex)
            }

            try {
                prefs.getStringSet(LOGIN_APP_SOURCES_LIST, null)
                        ?.also { builder.parseSourceMetadata(it) }
            } catch (ex: JSONException) {
                logger.warn("Cannot parse source metadata parseHeaders", ex)
            }
            try {
                prefs.getStringSet(LOGIN_SOURCE_TYPES, null)
                        ?.also { builder.parseSourceTypes(it) }
            } catch (ex: JSONException) {
                logger.warn("Cannot parse source types parseHeaders", ex)
            }

            return builder.apply {
                projectId = prefs.getString(LOGIN_PROJECT_ID, null)
                userId =prefs.getString(LOGIN_USER_ID, null)
                token = prefs.getString(LOGIN_TOKEN, null)
                tokenType = prefs.getInt(LOGIN_TOKEN_TYPE, 0)
                expiration = prefs.getLong(LOGIN_EXPIRATION, 0L)
                parseAttributes(prefs.getString(LOGIN_ATTRIBUTES, null))
                parseHeaders(prefs.getString(LOGIN_HEADERS_LIST, null))
                isPrivacyPolicyAccepted = prefs.getBoolean(LOGIN_PRIVACY_POLICY_ACCEPTED, false)
                needsRegisteredSources = prefs.getBoolean(LOGIN_NEEDS_REGISTERD_SOURCES, true)
            }.build()
        }
    }
}
