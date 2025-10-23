package org.radarcns.detail

import org.radarbase.android.auth.AppAuthState
import org.radarbase.android.auth.AuthService
import org.radarbase.android.auth.LoginManager
import org.radarbase.android.auth.oauth2.OAuth2LoginManager
import org.radarbase.android.auth.portal.ManagementPortalLoginManager
import org.radarbase.android.auth.sep.SEPLoginManager

class AuthServiceImpl : AuthService() {
    override fun createLoginManagers(appAuth: AppAuthState): List<LoginManager> = listOf(
        ManagementPortalLoginManager(this, appAuth),
        SEPLoginManager(this, appAuth),
        OAuth2LoginManager(this, appAuth)
    )

    override fun showLoginNotification() = Unit
}
