package org.radarbase.android.auth.portal

import org.json.JSONException
import org.json.JSONObject
import org.radarbase.android.auth.AppAuthState
import org.radarbase.android.auth.AuthService.Companion.BASE_URL_PROPERTY
import org.radarbase.android.auth.AuthService.Companion.PRIVACY_POLICY_URL_PROPERTY
import org.radarbase.android.auth.AuthStringParser
import org.radarbase.android.auth.portal.ManagementPortalClient.Companion.MP_REFRESH_TOKEN_PROPERTY
import org.radarbase.android.auth.portal.ManagementPortalLoginManager.Companion.SOURCE_TYPE
import java.io.IOException

/**
 * Reads refreshToken and meta-data of token from json string and sets it as property in
 * [AppAuthState].
 */
class MetaTokenParser(private val currentState: AppAuthState) : AuthStringParser {

    @Throws(IOException::class)
    override fun parse(value: String): AppAuthState {
        try {
            val json = JSONObject(value)
            return currentState.alter {
                attributes[MP_REFRESH_TOKEN_PROPERTY] = json.getString("refreshToken")
                attributes[PRIVACY_POLICY_URL_PROPERTY] = json.getString("privacyPolicyUrl")
                attributes[BASE_URL_PROPERTY] = json.getString("baseUrl")
                needsRegisteredSources = true
                authenticationSource = SOURCE_TYPE
            }
        } catch (ex: JSONException) {
            throw IOException("Failed to parse json string $value", ex)
        }
    }
}
