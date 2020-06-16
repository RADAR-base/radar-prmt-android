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
import org.json.JSONException
import org.radarbase.android.RadarApplication.Companion.radarApp
import org.radarbase.android.auth.*
import org.radarbase.producer.AuthenticationException

/**
 * Authenticates against the RADAR Management Portal.
 */
class OAuth2LoginManager(private val service: AuthService, private val projectIdClaim: String, private val userIdClaim: String) : LoginManager, LoginListener {
    private val stateManager: OAuth2StateManager = OAuth2StateManager(service)

    override fun refresh(authState: AppAuthState): Boolean {
        if (authState.tokenType != LoginManager.AUTH_TYPE_BEARER) {
            return false
        }
        return authState.getAttribute(LOGIN_REFRESH_TOKEN)
                ?.also { stateManager.refresh(service, it) } != null
    }


    override fun isRefreshable(authState: AppAuthState): Boolean =
        authState.userId != null && authState.getAttribute(LOGIN_REFRESH_TOKEN) != null

    override fun start(authState: AppAuthState) {
        service.radarApp.let { app ->
            stateManager.login(service, app.loginActivity, app.configuration)
        }
    }

    override fun onActivityCreate(activity: Activity): Boolean {
        stateManager.updateAfterAuthorization(service, activity.intent)
        return true
    }

    override fun invalidate(authState: AppAuthState, disableRefresh: Boolean): AppAuthState? {
        return when {
            authState.tokenType != LoginManager.AUTH_TYPE_BEARER -> return null
            disableRefresh -> authState.alter {
                attributes -= LOGIN_REFRESH_TOKEN
                isPrivacyPolicyAccepted = false
            }
            else -> authState
        }
    }

    override val sourceTypes: List<String> = OAUTH2_SOURCE_TYPES

    @Throws(AuthenticationException::class)
    override fun registerSource(authState: AppAuthState, source: SourceMetadata,
                       success: (AppAuthState, SourceMetadata) -> Unit,
                       failure: (Exception?) -> Unit): Boolean {
        success(authState, source)
        return true
    }

    override fun onDestroy() = Unit

    override fun loginSucceeded(manager: LoginManager?, authState: AppAuthState) {
        val token = authState.token
        if (token == null) {
            this.service.loginFailed(this,
                    IllegalArgumentException("Cannot login using OAuth2 without a token"))
            return
        }
        try {
            processJwt(authState, Jwt.parse(token)).let {
                this.service.loginSucceeded(this, it)
            }
        } catch (ex: JSONException) {
            this.service.loginFailed(this, ex)
        }

    }

    private fun processJwt(authState: AppAuthState, jwt: Jwt): AppAuthState {
        val body = jwt.body

        return authState.alter {
            authenticationSource = OAUTH2_SOURCE_TYPE
            needsRegisteredSources = false
            projectId = body.optString(projectIdClaim)
            userId = body.optString(userIdClaim)
            expiration = body.optLong("exp", java.lang.Long.MAX_VALUE / 1000L) * 1000L
        }
    }

    override fun loginFailed(manager: LoginManager?, ex: Exception?) = this.service.loginFailed(this, ex)

    companion object {
        private const val OAUTH2_SOURCE_TYPE = "org.radarcns.android.auth.oauth2.OAuth2LoginManager"
        private val OAUTH2_SOURCE_TYPES = listOf(OAUTH2_SOURCE_TYPE)
        const val LOGIN_REFRESH_TOKEN = "org.radarcns.auth.OAuth2LoginManager.refreshToken"
    }
}
