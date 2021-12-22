package org.radarcns.detail

import org.radarbase.android.auth.AppAuthState
import org.radarbase.android.auth.AuthService
import org.radarbase.android.auth.LoginManager
import org.radarbase.android.auth.portal.ManagementPortalLoginManager

class AuthServiceImpl : AuthService() {
    override fun createLoginManagers(appAuth: AppAuthState): List<LoginManager> = listOf(
        ManagementPortalLoginManager(this, appAuth),
    )

    override fun showLoginNotification() = Unit
}
