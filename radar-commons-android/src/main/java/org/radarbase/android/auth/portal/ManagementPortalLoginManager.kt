package org.radarbase.android.auth.portal

import android.app.Activity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONException
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarbase.android.RadarConfiguration.Companion.MANAGEMENT_PORTAL_URL_KEY
import org.radarbase.android.RadarConfiguration.Companion.OAUTH2_CLIENT_ID
import org.radarbase.android.RadarConfiguration.Companion.OAUTH2_CLIENT_SECRET
import org.radarbase.android.RadarConfiguration.Companion.RADAR_CONFIGURATION_CHANGED
import org.radarbase.android.RadarConfiguration.Companion.UNSAFE_KAFKA_CONNECTION
import org.radarbase.android.auth.AppAuthState
import org.radarbase.android.auth.AuthService
import org.radarbase.android.auth.LoginManager
import org.radarbase.android.auth.SourceMetadata
import org.radarbase.android.auth.portal.ManagementPortalClient.Companion.MP_REFRESH_TOKEN_PROPERTY
import org.radarbase.android.util.BroadcastRegistration
import org.radarbase.android.util.register
import org.radarbase.config.ServerConfig
import org.radarbase.producer.AuthenticationException
import org.radarbase.producer.rest.RestClient
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.MalformedURLException
import java.util.concurrent.locks.ReentrantLock

class ManagementPortalLoginManager(private val listener: AuthService, state: AppAuthState) : LoginManager {
    private val sources: MutableMap<String, SourceMetadata> = mutableMapOf()
    private val firebaseUpdateReceiver: BroadcastRegistration

    private var client: ManagementPortalClient? = null
    private var restClient: RestClient? = null
    private val refreshLock: ReentrantLock
    private val config = listener.radarConfig

    init {
        ensureClientConnectivity()

        firebaseUpdateReceiver = LocalBroadcastManager.getInstance(listener)
                .register(RADAR_CONFIGURATION_CHANGED) { _, _ -> ensureClientConnectivity() }
        updateSources(state)
        refreshLock = ReentrantLock()
    }

    fun setRefreshToken(authState: AppAuthState, refreshToken: String) {
        refresh(authState.alter { attributes[MP_REFRESH_TOKEN_PROPERTY] = refreshToken })
    }

    fun setTokenFromUrl(authState: AppAuthState, refreshTokenUrl: String) {
        client?.let { client ->
            if (refreshLock.tryLock()) {
                try {
                    // create parser
                    val parser = MetaTokenParser(authState)

                    // retrieve token and update authState
                    client.getRefreshToken(refreshTokenUrl, parser).let { authState ->
                        // update radarConfig
                        if (config.updateWithAuthState(listener, authState)) {
                            config.persistChanges()
                            // refresh client
                            ensureClientConnectivity()
                        }
                        logger.info("Retrieved refreshToken from url")
                        // refresh token
                        refresh(authState)
                    }
                } catch (ex: Exception) {
                    logger.error("Failed to get meta token", ex)
                    listener.loginFailed(this, ex)
                } finally {
                    refreshLock.unlock()
                }
            }
        }
    }

    override fun refresh(authState: AppAuthState): Boolean {
        if (authState.getAttribute(MP_REFRESH_TOKEN_PROPERTY) == null) {
            return false
        }
        client?.let { client ->
            if (refreshLock.tryLock()) {
                try {
                    val parser = SubjectTokenParser(client, authState)

                    client.refreshToken(authState, parser).let { authState ->
                        logger.info("Refreshed JWT")

                        updateSources(authState)
                        listener.loginSucceeded(this, authState)
                    }
                } catch (ex: Exception) {
                    logger.error("Failed to get access token", ex)
                    listener.loginFailed(this, ex)
                } finally {
                    refreshLock.unlock()
                }
            }
        }
        return true
    }

    override fun isRefreshable(authState: AppAuthState): Boolean {
        return authState.userId != null
                && authState.projectId != null
                && authState.getAttribute(MP_REFRESH_TOKEN_PROPERTY) != null
    }

    override fun start(authState: AppAuthState) {
        refresh(authState)
    }

    override fun onActivityCreate(activity: Activity): Boolean {
        return false
    }

    override fun invalidate(authState: AppAuthState, disableRefresh: Boolean): AppAuthState? {
        if (authState.authenticationSource != SOURCE_TYPE) {
            return null
        }
        return authState.alter {
            attributes -= MP_REFRESH_TOKEN_PROPERTY
            isPrivacyPolicyAccepted = false
        }
    }

    override val sourceTypes: List<String> = sourceTypeList

    override fun registerSource(authState: AppAuthState, source: SourceMetadata,
                                success: (AppAuthState, SourceMetadata) -> Unit,
                                failure: (Exception?) -> Unit): Boolean {
        logger.debug("Handling source registration")

        sources[source.sourceId]?.also { resultSource ->
            success(authState, resultSource)
            return true
        }

        client?.let { client ->
            try {
                val updatedSource = if (source.sourceId == null) {
                    // temporary measure to reuse existing source IDs if they exist
                    source.type?.id
                            ?.let { sourceType -> sources.values.find { it.type?.id == sourceType } }
                            ?.let {
                                source.sourceId = it.sourceId
                                source.sourceName = it.sourceName
                                client.updateSource(authState, source)
                            }
                            ?: client.registerSource(authState, source)
                } else {
                    client.updateSource(authState, source)
                }
                success(addSource(authState, updatedSource), updatedSource)
            } catch (ex: UnsupportedOperationException) {
                logger.warn("ManagementPortal does not support updating the app source.")
                success(addSource(authState, source), source)
            } catch (ex: ConflictException) {
                try {
                    client.getSubject(authState, GetSubjectParser(authState)).let { authState ->
                        updateSources(authState)
                        sources[source.sourceId]?.let { source ->
                            success(authState, source)
                        } ?: failure(IllegalStateException("Source was not added to ManagementPortal, even though conflict was reported."))
                    }
                } catch (ioex: IOException) {
                    logger.error("Failed to register source {} with {}: already registered",
                            source.sourceName, source.type, ex)

                    failure(ex)
                }
            } catch (ex: java.lang.IllegalArgumentException) {
                logger.error("Source {} is not valid", source)
                failure(ex)
            } catch (ex: AuthenticationException) {
                listener.invalidate(authState.token, false)
                logger.error("Authentication error; failed to register source {} of type {}",
                        source.sourceName, source.type, ex)
                failure(ex)
            } catch (ex: IOException) {
                logger.error("Failed to register source {} with {}",
                        source.sourceName, source.type, ex)
                failure(ex)
            } catch (ex: JSONException) {
                logger.error("Failed to register source {} with {}",
                        source.sourceName, source.type, ex)
                failure(ex)
            }
        } ?: failure(IllegalStateException("Cannot register source without a client"))

        return true
    }


    private fun updateSources(authState: AppAuthState) {
        authState.sourceMetadata
                .forEach { sourceMetadata ->
                    sourceMetadata.sourceId?.let {
                        sources[it] = sourceMetadata
                    }
                }
    }

    override fun onDestroy() {
        firebaseUpdateReceiver.unregister()
    }

    @Synchronized
    private fun ensureClientConnectivity() {
        val url = config.getString(MANAGEMENT_PORTAL_URL_KEY)
        val unsafe = config.getBoolean(UNSAFE_KAFKA_CONNECTION, false)
        try {
            val portalConfig = ServerConfig(url)
            portalConfig.isUnsafe = unsafe
            client = ManagementPortalClient(portalConfig,
                    config.getString(OAUTH2_CLIENT_ID),
                    config.getString(OAUTH2_CLIENT_SECRET, ""), client = restClient)
                    .also { restClient = it.client }
        } catch (e: MalformedURLException) {
            logger.error("Cannot construct ManagementPortalClient with malformed URL")
            client = null
        } catch (e: IllegalArgumentException) {
            logger.error("Cannot construct ManagementPortalClient without client credentials")
            client = null
        }
    }

    private fun addSource(authState: AppAuthState, source: SourceMetadata): AppAuthState {
        val sourceId = source.sourceId
        return if (sourceId == null) {
            logger.error("Cannot add source {} without ID", source)
            authState
        } else {
            sources[sourceId] = source

            authState.alter {
                val existing = sourceMetadata.filter { it.sourceId == source.sourceId }
                if (existing.isEmpty()) {
                    invalidate()
                } else {
                    sourceMetadata -= existing
                }
                sourceMetadata += source
            }
        }
    }

    companion object {
        const val SOURCE_TYPE = "org.radarcns.auth.portal.ManagementPortal"
        private val logger = LoggerFactory.getLogger(ManagementPortalLoginManager::class.java)
        val sourceTypeList = listOf(SOURCE_TYPE)
    }
}
