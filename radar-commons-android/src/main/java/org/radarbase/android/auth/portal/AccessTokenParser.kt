package org.radarbase.android.auth.portal

import org.json.JSONException
import org.json.JSONObject
import org.radarbase.android.auth.AppAuthState
import org.radarbase.android.auth.AuthStringParser
import org.radarbase.android.auth.LoginManager.Companion.AUTH_TYPE_BEARER
import org.radarbase.android.auth.portal.ManagementPortalClient.Companion.MP_REFRESH_TOKEN_PROPERTY
import org.radarbase.android.auth.portal.ManagementPortalLoginManager.Companion.SOURCE_TYPE
import java.io.IOException
import java.util.concurrent.TimeUnit

class AccessTokenParser(private val state: AppAuthState) : AuthStringParser {

    @Throws(IOException::class)
    override fun parse(value: String): AppAuthState {
        var refreshToken = state.getAttribute(MP_REFRESH_TOKEN_PROPERTY)
        try {
            val json = JSONObject(value)
            val accessToken = json.getString("access_token")
            refreshToken = json.optString("refresh_token", refreshToken)
            return state.alter {
                attributes[MP_REFRESH_TOKEN_PROPERTY] = refreshToken
                setHeader("Authorization", "Bearer $accessToken")

                token = accessToken
                tokenType = AUTH_TYPE_BEARER
                userId = json.getString("sub")
                expiration = TimeUnit.SECONDS.toMillis(json.optLong("expires_in", 3600L)) + System.currentTimeMillis()
                needsRegisteredSources = true
                authenticationSource = SOURCE_TYPE
            }
        } catch (ex: JSONException) {
            throw IOException("Failed to parse json string $value", ex)
        }
    }
}
