package org.radarbase.passive.app

import org.radarbase.android.auth.AppAuthState
import org.radarbase.android.auth.AuthService
import org.radarbase.android.auth.LoginManager
import org.radarbase.android.auth.portal.ManagementPortalLoginManager

class AuthService : AuthService() {
    override fun createLoginManagers(appAuth: AppAuthState): List<LoginManager> {
        return listOf<LoginManager>(ManagementPortalLoginManager(this, appAuth))
    }

    override fun showLoginNotification() {}
}
