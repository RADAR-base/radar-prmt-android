package org.radarbase.android.auth

import android.content.Context
import androidx.annotation.Keep
import org.radarbase.android.RadarApplication
import org.radarbase.android.util.ManagedServiceConnection
import org.slf4j.LoggerFactory

@Keep
open class AuthServiceConnection(context: Context, private val listener: LoginListener):
        ManagedServiceConnection<AuthService.AuthServiceBinder>(context, (context.applicationContext as RadarApplication).authService) {
    private lateinit var registration: AuthService.LoginListenerRegistration

    init {
        onBoundListeners += { binder ->
            registration = binder.addLoginListener(listener)
            binder.refreshIfOnline()
        }

        onUnboundListeners += { binder ->
            logger.info("Unbound authentication service")
            binder.removeLoginListener(registration)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthServiceConnection::class.java)
    }
}
