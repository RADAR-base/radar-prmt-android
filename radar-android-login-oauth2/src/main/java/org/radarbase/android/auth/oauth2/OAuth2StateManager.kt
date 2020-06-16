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

package org.radarbase.android.auth.oauth2

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.AnyThread
import net.openid.appauth.*
import org.json.JSONException
import org.radarbase.android.RadarConfiguration
import org.radarbase.android.auth.AppAuthState
import org.radarbase.android.auth.AuthService
import org.radarbase.android.auth.LoginManager
import org.radarbase.android.auth.oauth2.OAuth2LoginManager.Companion.LOGIN_REFRESH_TOKEN
import org.slf4j.LoggerFactory

class OAuth2StateManager(context: Context) {
    private val mPrefs: SharedPreferences
    private var mCurrentAuthState: AuthState? = null

    init {
        mPrefs = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
        mCurrentAuthState = readState()
    }

    @AnyThread
    @Synchronized
    fun login(context: AuthService, activityClass: Class<out Activity>, config: RadarConfiguration) {
        val authorizeUri = Uri.parse(config.getString(RadarConfiguration.OAUTH2_AUTHORIZE_URL))
        val tokenUri = Uri.parse(config.getString(RadarConfiguration.OAUTH2_TOKEN_URL))
        val redirectUri = Uri.parse(config.getString(RadarConfiguration.OAUTH2_REDIRECT_URL))
        val clientId = config.getString(RadarConfiguration.OAUTH2_CLIENT_ID)

        val authConfig = AuthorizationServiceConfiguration(authorizeUri, tokenUri, null)

        val authRequestBuilder = AuthorizationRequest.Builder(
                authConfig, // the authorization service configuration
                clientId, // the client ID, typically pre-registered and static
                ResponseTypeValues.CODE, // the response_type value: we want a code
                redirectUri) // the redirect URI to which the auth response is sent

        val service = AuthorizationService(context)
        service.performAuthorizationRequest(
                authRequestBuilder.build(),
                PendingIntent.getActivity(context,
                        OAUTH_INTENT_HANDLER_REQUEST_CODE,
                        Intent(context, activityClass),
                        PendingIntent.FLAG_ONE_SHOT))
    }

    @AnyThread
    @Synchronized
    fun updateAfterAuthorization(authService: AuthService, intent: Intent?) {
        if (intent == null) {
            return
        }

        val resp = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        if (resp != null || ex != null) {
            mCurrentAuthState!!.update(resp, ex)
            writeState(mCurrentAuthState)
        }

        if (resp != null) {
            val service = AuthorizationService(authService)
            // authorization succeeded
            service.performTokenRequest(
                    resp.createTokenExchangeRequest(), processTokenResponse(authService))
        } else if (ex != null) {
            authService.loginFailed(null, ex)
        }
    }

    @Synchronized
    fun refresh(context: AuthService, refreshToken: String?) {
        val service = AuthorizationService(context)
        // refreshToken does not originate from the current auth state.
        if (refreshToken != null && refreshToken != mCurrentAuthState!!.refreshToken) {
            try {
                val json = mCurrentAuthState!!.jsonSerialize()
                json.put("refreshToken", refreshToken)
                mCurrentAuthState = AuthState.jsonDeserialize(json)
            } catch (e: JSONException) {
                logger.error("Failed to update refresh token")
            }

        }
        // authorization succeeded
        service.performTokenRequest(
                mCurrentAuthState!!.createTokenRefreshRequest(), processTokenResponse(context))
    }

    private fun processTokenResponse(context: AuthService): AuthorizationService.TokenResponseCallback {
        return AuthorizationService.TokenResponseCallback { resp, ex ->
            if (resp != null) {
                mCurrentAuthState?.also { authState ->
                    updateAfterTokenResponse(resp, ex)
                    var expiration = authState.accessTokenExpirationTime
                    if (expiration == null) {
                        expiration = 0L
                    }
                    context.loginSucceeded(null, AppAuthState {
                        token = authState.accessToken!!
                                .also { addHeader("Authorization","Bearer $it") }
                        tokenType = LoginManager.AUTH_TYPE_BEARER
                        this.expiration = expiration
                        attributes[LOGIN_REFRESH_TOKEN] = authState.refreshToken!!
                    })
                }
            } else {
                context.loginFailed(null, ex)
            }
        }
    }

    @AnyThread
    @Synchronized
    fun updateAfterTokenResponse(
            response: TokenResponse?,
            ex: AuthorizationException?) {
        mCurrentAuthState!!.update(response, ex)
        writeState(mCurrentAuthState)
    }

    @AnyThread
    @Synchronized
    private fun readState(): AuthState {
        val currentState = mPrefs.getString(KEY_STATE, null) ?: return AuthState()

        return try {
            AuthState.jsonDeserialize(currentState)
        } catch (ex: JSONException) {
            logger.warn("Failed to deserialize stored auth state - discarding", ex)
            writeState(null)
            AuthState()
        }

    }

    @AnyThread
    @Synchronized
    private fun writeState(state: AuthState?) {
        if (state != null) {
            mPrefs.edit().putString(KEY_STATE, state.jsonSerializeString()).apply()
        } else {
            mPrefs.edit().remove(KEY_STATE).apply()
        }
    }

    companion object {
        private const val OAUTH_INTENT_HANDLER_REQUEST_CODE = 8422341

        private val logger = LoggerFactory.getLogger(OAuth2StateManager::class.java)

        private const val STORE_NAME = "AuthState"
        private const val KEY_STATE = "state"
    }
}
