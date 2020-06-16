package org.radarbase.android.auth

import android.app.Service
import android.content.Intent
import android.content.Intent.*
import android.os.Binder
import android.os.IBinder
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import androidx.annotation.Keep
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.radarbase.android.RadarApplication
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarbase.android.RadarConfiguration
import org.radarbase.android.auth.LoginActivity.Companion.ACTION_LOGIN_SUCCESS
import org.radarbase.android.util.NetworkConnectedReceiver
import org.radarbase.android.util.SafeHandler
import org.radarbase.android.util.send
import org.slf4j.LoggerFactory
import java.net.ConnectException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Keep
abstract class AuthService : Service(), LoginListener {
    private lateinit var appAuth: AppAuthState
    lateinit var loginManagers: List<LoginManager>
    private val listeners: MutableList<LoginListenerRegistration> = mutableListOf()
    lateinit var config: RadarConfiguration
    val handler = SafeHandler.getInstance("AuthService", THREAD_PRIORITY_BACKGROUND)
    var loginListenerId: Long = 0
    private lateinit var networkConnectedListener: NetworkConnectedReceiver
    private var configRegistration: LoginListenerRegistration? = null
    private var currentDelay: Long? = null
    private var isConnected: Boolean = false
    @Volatile
    private var isInLoginActivity: Boolean = false

    private lateinit var broadcaster: LocalBroadcastManager

    override fun onCreate() {
        super.onCreate()
        broadcaster = LocalBroadcastManager.getInstance(this)
        appAuth = AppAuthState.from(this)
        config = radarConfig
        config.updateWithAuthState(this, appAuth)
        handler.start()
        loginManagers = createLoginManagers(appAuth)
        networkConnectedListener = NetworkConnectedReceiver(this, object : NetworkConnectedReceiver.NetworkConnectedListener {
            override fun onNetworkConnectionChanged(state: NetworkConnectedReceiver.NetworkState) {
                handler.execute {
                    isConnected = state.isConnected
                    if (isConnected && !appAuth.isValidFor(5, TimeUnit.MINUTES)) {
                        refresh()
                    }
                }
            }
        })
        networkConnectedListener.register()

        configRegistration = addLoginListener(object : LoginListener {
            override fun loginFailed(manager: LoginManager?, ex: java.lang.Exception?) {
                // no action required
            }

            override fun loginSucceeded(manager: LoginManager?, authState: AppAuthState) {
                currentDelay = null
                config.updateWithAuthState(this@AuthService, appAuth)
                config.persistChanges()
                appAuth.addToPreferences(this@AuthService)
            }
        })
    }

    /**
     * Refresh the access token. If the existing token is still valid, do not refresh.
     * The request gets handled in a separate thread and returns a result via loginSucceeded. If
     * the current authentication is not valid and it is not possible to refresh the authentication
     * this opens the login activity.
     */
    fun refresh() {
        handler.execute {
            logger.info("Refreshing authentication state")
            if (appAuth.isValidFor(5, TimeUnit.MINUTES)) {
                callListeners(sinceUpdate = appAuth.lastUpdate) {
                    it.loginListener.loginSucceeded(null, appAuth)
                }
            } else {
                if (!relevantManagers.any { it.refresh(appAuth)}) {
                    startLogin()
                }
            }
        }
    }

    /**
     * Refresh the access token. If the existing token is still valid, do not refresh.
     * The request gets handled in a separate thread and returns a result via loginSucceeded. If
     * there is no network connectivity, just check if the authentication could be refreshed if the
     * client was online.
     */
    fun refreshIfOnline() {
        handler.execute {
            if (isConnected) {
                refresh()
            } else if (appAuth.isValid
                    || relevantManagers.any { it.isRefreshable(appAuth) }) {
                logger.info("Retrieving active authentication state without refreshing")
                callListeners(sinceUpdate = appAuth.lastUpdate) {
                    it.loginListener.loginSucceeded(null, appAuth)
                }
            } else {
                logger.info("Failed to retrieve authentication state without refreshing")
                startLogin()
            }
        }
    }

    protected open fun startLogin() {
        if (!isInLoginActivity) {
            try {
                logger.info("Starting login activity")
                val intent = Intent(this, (application as RadarApplication).loginActivity)
                intent.flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_TASK_ON_HOME or FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            } catch (ex: IllegalStateException) {
                logger.warn("Failed to start login activity. Notifying about login.")
                showLoginNotification()
            }
        }
    }

    protected abstract fun showLoginNotification()

    override fun loginFailed(manager: LoginManager?, ex: java.lang.Exception?) {
        handler.executeReentrant {
            logger.info("Login failed: {}", ex?.toString())
            callListeners {
                it.loginListener.loginFailed(manager, ex)
            }

            if (ex is ConnectException) {
                isConnected = false

                val actualDelay = Math.min(2 * (currentDelay ?: RETRY_MIN_DELAY), RETRY_MAX_DELAY)
                        .also { currentDelay = it }
                        .let { Random.nextLong(RETRY_MIN_DELAY, it) }
                handler.delay(actualDelay, ::refresh)
            } else {
                if (networkConnectedListener.state.isConnected) {
                    startLogin()
                }
            }
        }
    }

    private fun callListeners(call: (LoginListenerRegistration) -> Unit) {
        synchronized(listeners) {
            listeners.forEach(call)
        }
    }

    private fun callListeners(sinceUpdate: Long, call: (LoginListenerRegistration) -> Unit) {
        synchronized(listeners) {
            listeners.filter { it.lastUpdate < sinceUpdate }
                    .forEach { listener ->
                        listener.lastUpdate = sinceUpdate
                        call(listener)
                    }
        }
    }

    private val relevantManagers: List<LoginManager>
        get() = appAuth.authenticationSource?.let { authSource ->
                loginManagers.filter { authSource in it.sourceTypes }
            } ?: loginManagers

    override fun loginSucceeded(manager: LoginManager?, authState: AppAuthState) {
        handler.executeReentrant {
            logger.info("Log in succeeded.")
            appAuth = authState

            broadcaster.send(ACTION_LOGIN_SUCCESS)

            callListeners(sinceUpdate = appAuth.lastUpdate) {
                it.loginListener.loginSucceeded(manager, appAuth)
            }
        }
    }

    override fun onDestroy() {
        networkConnectedListener.unregister()
        configRegistration?.let { removeLoginListener(it) }
        handler.stop {
            loginManagers.forEach { it.onDestroy() }
            appAuth.addToPreferences(this)
        }
    }

    fun update(manager: LoginManager) {
        handler.execute {
            logger.info("Refreshing manager {}", manager)
            manager.refresh(appAuth)
        }
    }

    fun addLoginListener(loginListener: LoginListener): LoginListenerRegistration {
        return synchronized(listeners) {
            LoginListenerRegistration(loginListener)
                    .also { listeners += it }
        }
    }

    fun removeLoginListener(registration: LoginListenerRegistration) {
        synchronized(listeners) {
            logger.debug("Removing login listener #{}: {} (starting with {} listeners)", registration.id, registration.loginListener, listeners.size)
            listeners -= listeners.filter { it.id == registration.id }
        }
    }

    /**
     * Create your login managers here. Call [LoginManager.start] for the login method that
     * a user indicates.
     * @param appAuth previous invalid authentication
     * @return non-empty list of login managers to use
     */
    protected abstract fun createLoginManagers(appAuth: AppAuthState): List<LoginManager>

    private fun updateState(update: AppAuthState.Builder.() -> Unit) {
        handler.executeReentrant {
            appAuth = appAuth.alter(update)
        }
    }

    private fun applyState(apply: (AppAuthState) -> Unit) {
        handler.executeReentrant {
            apply(appAuth)
        }
    }

    fun invalidate(token: String?, disableRefresh: Boolean) {
        handler.executeReentrant {
            logger.info("Invalidating authentication state")
            if (token?.let { it == appAuth.token } != false) {
                appAuth = appAuth.alter { invalidate() }

                if (relevantManagers.any { manager ->
                    manager.invalidate(appAuth, disableRefresh)
                            ?.also { appAuth = it } != null
                }) {
                    appAuth.addToPreferences(this)
                    startLogin()
                }
            }
        }
    }

    private fun registerSource(source: SourceMetadata, success: (AppAuthState, SourceMetadata) -> Unit, failure: (Exception?) -> Unit) {
        handler.execute {
            logger.info("Registering source with {}: {}", source.type, source.sourceId)

            relevantManagers.any {
                it.registerSource(appAuth, source, success, failure)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return AuthServiceBinder()
    }

    inner class AuthServiceBinder: Binder() {
        fun addLoginListener(loginListener: LoginListener): LoginListenerRegistration = this@AuthService.addLoginListener(loginListener)

        fun removeLoginListener(registration: LoginListenerRegistration) = this@AuthService.removeLoginListener(registration)

        val managers: List<LoginManager>
            get() = loginManagers

        fun update(manager: LoginManager) = this@AuthService.update(manager)

        fun refresh() = this@AuthService.refresh()

        fun refreshIfOnline() = this@AuthService.refreshIfOnline()

        fun applyState(apply: (AppAuthState) -> Unit) = this@AuthService.applyState(apply)

        @Suppress("unused")
        fun updateState(update: AppAuthState.Builder.() -> Unit) = this@AuthService.updateState(update)

        fun registerSource(source: SourceMetadata, success: (AppAuthState, SourceMetadata) -> Unit, failure: (Exception?) -> Unit) =
                this@AuthService.registerSource(source, success, failure)

        fun invalidate(token: String?, disableRefresh: Boolean) = this@AuthService.invalidate(token, disableRefresh)

        var isInLoginActivity: Boolean
            get() = this@AuthService.isInLoginActivity
            set(value) {
                this@AuthService.isInLoginActivity = value
            }
    }

    inner class LoginListenerRegistration(val loginListener: LoginListener) {
        val id = ++loginListenerId
        var lastUpdate = 0L
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthService::class.java)
        private const val RETRY_MIN_DELAY = 300L
        private const val RETRY_MAX_DELAY = 86400L
        const val PRIVACY_POLICY_URL_PROPERTY = "org.radarcns.android.auth.portal.ManagementPortalClient.privacyPolicyUrl"
        const val BASE_URL_PROPERTY = "org.radarcns.android.auth.portal.ManagementPortalClient.baseUrl"
    }
}
