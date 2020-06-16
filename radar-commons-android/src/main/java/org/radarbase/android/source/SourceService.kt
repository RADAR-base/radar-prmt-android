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

package org.radarbase.android.source

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import androidx.annotation.CallSuper
import androidx.annotation.Keep
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.apache.avro.specific.SpecificRecord
import org.radarbase.android.RadarApplication.Companion.radarApp
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarbase.android.RadarConfiguration
import org.radarbase.android.auth.*
import org.radarbase.android.data.DataHandler
import org.radarbase.android.source.SourceProvider.Companion.MODEL_KEY
import org.radarbase.android.source.SourceProvider.Companion.NEEDS_BLUETOOTH_KEY
import org.radarbase.android.source.SourceProvider.Companion.PRODUCER_KEY
import org.radarbase.android.util.BundleSerialization
import org.radarbase.android.util.ManagedServiceConnection
import org.radarbase.android.util.SafeHandler
import org.radarbase.android.util.send
import org.radarcns.kafka.ObservationKey
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap

/**
 * A service that manages a SourceManager and a TableDataHandler to send addToPreferences the data of a
 * wearable device, phone or API and send it to a Kafka REST proxy.
 *
 * Specific wearables should extend this class.
 */
@Keep
abstract class SourceService<T : BaseSourceState> : Service(), SourceStatusListener, LoginListener {
    val key = ObservationKey()

    @get:Synchronized
    @set:Synchronized
    var dataHandler: DataHandler<ObservationKey, SpecificRecord>? = null
    @get:Synchronized
    var sourceManager: SourceManager<T>? = null
        private set
    private lateinit var mBinder: SourceServiceBinder<T>
    private var hasBluetoothPermission: Boolean = false
    lateinit var sources: Set<SourceMetadata>
    lateinit var sourceTypes: Set<SourceType>
    private lateinit var authConnection: AuthServiceConnection
    protected lateinit var config: RadarConfiguration
    private lateinit var radarConnection: ManagedServiceConnection<org.radarbase.android.IRadarBinder>
    private lateinit var handler: SafeHandler
    private var startFuture: SafeHandler.HandlerFuture? = null
    private lateinit var broadcaster: LocalBroadcastManager
    private lateinit var sourceModel: String
    private lateinit var sourceProducer: String

    val state: T
        get() {
            return sourceManager?.state ?: defaultState.apply {
                id.apply {
                    setProjectId(key.getProjectId())
                    setUserId(key.getUserId())
                    setSourceId(key.getSourceId())
                }
            }
        }

    /**
     * Default state when no source manager is active.
     */
    protected abstract val defaultState: T

    private val expectedSourceNames: Set<String>
        get() = HashSet<String>(sources
                .map { it.expectedSourceName }
                .filter { it != null })!!

    open val isBluetoothConnectionRequired: Boolean
        get() = hasBluetoothPermission

    @CallSuper
    override fun onCreate() {
        logger.info("Creating SourceService {}", this)
        super.onCreate()
        sources = HashSet()
        sourceTypes = HashSet()
        broadcaster = LocalBroadcastManager.getInstance(this)

        mBinder = createBinder()

        authConnection = AuthServiceConnection(this, this)
        authConnection.bind()

        radarConnection = ManagedServiceConnection(this, radarApp.radarService)
        radarConnection.onBoundListeners.add { binder ->
            dataHandler = binder.dataHandler
            handler.execute {
                startFuture?.runNow()
            }
        }
        radarConnection.bind()
        handler = SafeHandler.getInstance("SourceService-$javaClass", THREAD_PRIORITY_BACKGROUND)

        config = radarConfig

        sourceManager = null
        startFuture = null
    }

    @CallSuper
    override fun onDestroy() {
        logger.info("Destroying SourceService {}", this)
        super.onDestroy()

        radarConnection.unbind()
        authConnection.unbind()

        handler.stop { stopSourceManager(unsetSourceManager()) }

        radarApp.onSourceServiceDestroy(this)
    }

    @CallSuper
    protected open fun configure(configuration: RadarConfiguration) {
        sourceManager?.let { configureSourceManager(it, configuration) }
    }

    protected open fun configureSourceManager(manager: SourceManager<T>, configuration: RadarConfiguration) {}

    override fun onBind(intent: Intent): SourceServiceBinder<T> {
        doBind(intent, true)
        return mBinder
    }

    @CallSuper
    override fun onRebind(intent: Intent) {
        doBind(intent, false)
    }

    private fun doBind(intent: Intent, firstBind: Boolean) {
        logger.debug("Received (re)bind in {}", this)
        val extras = BundleSerialization.getPersistentExtras(intent, this) ?: Bundle()
        onInvocation(extras)

        radarApp.onSourceServiceInvocation(this, extras, firstBind)
    }

    override fun onUnbind(intent: Intent): Boolean {
        logger.debug("Received unbind in {}", this)
        return true
    }

    override fun sourceFailedToConnect(name: String) {
        broadcaster.send(SOURCE_CONNECT_FAILED) {
            putExtra(SOURCE_SERVICE_CLASS, this@SourceService.javaClass.name)
            putExtra(SOURCE_STATUS_NAME, name)
        }
    }

    private fun broadcastSourceStatus(name: String?, status: SourceStatusListener.Status) {
        broadcaster.send(SOURCE_STATUS_CHANGED) {
            putExtra(SOURCE_STATUS_CHANGED, status.ordinal)
            putExtra(SOURCE_SERVICE_CLASS, this@SourceService.javaClass.name)
            name?.let { putExtra(SOURCE_STATUS_NAME, it) }
        }
    }

    override fun sourceStatusUpdated(manager: SourceManager<*>, status: SourceStatusListener.Status) {
        if (status == SourceStatusListener.Status.DISCONNECTING) {
            handler.execute(true) {
                if (this.sourceManager === manager) {
                    this.sourceManager = null
                }
                stopSourceManager(manager)
            }
        }
        broadcastSourceStatus(manager.name, status)
    }

    @Synchronized
    private fun unsetSourceManager(): SourceManager<*>? {
        return sourceManager.also {
            sourceManager = null
            handler.stop { }
        }
    }

    private fun stopSourceManager(manager: SourceManager<*>?) {
        try {
            manager?.takeUnless(SourceManager<*>::isClosed)?.close()
        } catch (e: IOException) {
            logger.warn("Failed to close source manager", e)
        }
    }

    /**
     * New source manager.
     */
    protected abstract fun createSourceManager(): SourceManager<T>

    fun startRecording(acceptableIds: Set<String>) {
        checkNotNull(key.getUserId()) { "Cannot start recording: user ID is not set." }

        handler.takeUnless(SafeHandler::isStarted)?.start()
        handler.execute {
            doStart(acceptableIds)
        }
    }

    private fun doStart(acceptableIds: Set<String>) {
        val expectedNames = expectedSourceNames
        val actualIds = if (expectedNames.isEmpty()) acceptableIds else expectedNames

        if (sourceManager == null) {
            if (isBluetoothConnectionRequired
                    && BluetoothAdapter.getDefaultAdapter()?.isEnabled == false) {
                logger.error("Cannot start recording without Bluetooth")
                return
            }
            if (dataHandler != null) {
                logger.info("Starting recording now for {}", javaClass.simpleName)
                if (sourceManager == null) {
                    createSourceManager().also { manager ->
                        sourceManager = manager
                        configureSourceManager(manager, config)
                        manager.start(actualIds)
                    }
                } else {
                    logger.warn("A SourceManager is already registered in the mean time for {}", javaClass.simpleName)
                }
            } else {
                startAfterDelay(acceptableIds)
            }
        } else if (sourceManager?.state?.status == SourceStatusListener.Status.DISCONNECTED) {
            logger.warn("A disconnected SourceManager is still registered for {}", javaClass.simpleName)
            startAfterDelay(acceptableIds)
        } else {
            logger.warn("A SourceManager is already registered for {}", javaClass.simpleName)
        }
    }

    private fun startAfterDelay(acceptableIds: Set<String>) {
        if (startFuture == null) {
            logger.warn("Starting recording soon for {}", javaClass.simpleName)
            startFuture = handler.delay(100) {
                startFuture?.let {
                    startFuture = null
                    doStart(acceptableIds)
                }
            }
        }
    }

    fun stopRecording() {
        handler.execute {
            startFuture?.let {
                it.cancel()
                startFuture = null
            }
            stopSourceManager(unsetSourceManager())
            logger.info("Stopped recording {}", this)
        }
    }

    override fun loginSucceeded(manager: LoginManager?, authState: AppAuthState) {
        key.setProjectId(authState.projectId)
        key.setUserId(authState.userId)
        sourceTypes = HashSet(authState.sourceTypes)
    }

    override fun loginFailed(manager: LoginManager?, ex: Exception?) {

    }

    /**
     * Override this function to get any parameters from the given intent.
     * Bundle classloader needs to be set correctly for this to work.
     *
     * @param bundle intent extras that the activity provided.
     */
    @CallSuper
    fun onInvocation(bundle: Bundle) {
        hasBluetoothPermission = bundle.getBoolean(NEEDS_BLUETOOTH_KEY, false)
        sourceProducer = requireNotNull(bundle.getString(PRODUCER_KEY)) { "Missing source producer" }
        sourceModel = requireNotNull(bundle.getString(MODEL_KEY)) { "Missing source model" }
        configure(config)
    }

    /** Get the service local binder.  */
    private fun createBinder() = SourceServiceBinder(this)

    private fun registerSource(type: SourceType, name: String, attributes: Map<String, String>) {
        logger.info("Registering source {} with attributes {}", type, attributes)

        val source = SourceMetadata(type).apply {
            sourceName = name
            this.attributes = attributes
        }

        val onFail: (Exception?) -> Unit = {
            logger.warn("Failed to register source: {}", it.toString())
            handler.delay(300_000L) { registerSource(type, name, attributes) }
        }

        authConnection.binder?.registerSource(source,
                { authState, updatedSource ->
                    key.setProjectId(authState.projectId)
                    key.setUserId(authState.userId)
                    key.setSourceId(updatedSource.sourceId)
                    source.sourceId = updatedSource.sourceId
                    source.sourceName = updatedSource.sourceName
                    source.expectedSourceName = updatedSource.expectedSourceName
                    sourceManager?.didRegister(source)
                }, onFail)
                ?: onFail(null)
    }

    open fun ensureRegistration(id: String?, name: String, attributes: Map<String, String>): Boolean {
        if (sourceTypes.isEmpty()) {
            handler.delay(5_000L) { ensureRegistration(id, name, attributes) }
        }

        val fullAttributes = HashMap(attributes).apply {
            this["physicalId"] = id ?: ""
            this["physicalName"] = name
        }
        return if (sources.isEmpty()) {
            matchingSourceType?.let { registerSource(it, name, fullAttributes) } != null
        } else {
            val matchingSource = sources
                    .find { source ->
                        when {
                            source.matches(id, name) -> true
                            id != null && source.attributes["physicalId"]?.isEmpty() == false -> source.attributes["physicalId"] == id
                            source.attributes["physicalName"]?.isEmpty() == false -> source.attributes["physicalName"] == name
                            else -> false
                        }
                    }

            if (matchingSource == null) {
                matchingSourceType?.let { registerSource(it, name, fullAttributes) } != null
            } else {
                sourceManager?.didRegister(matchingSource)
                true
            }
        }
    }

    private val matchingSourceType: SourceType?
        get() = sourceTypes
                .filter { it.producer == sourceProducer && it.model == sourceModel }
                .sortedBy { if (it.catalogVersion[0] == 'v') it.catalogVersion.substring(1) else it.catalogVersion }
                .lastOrNull()

    override fun toString() = "${javaClass.simpleName}<${sourceManager?.name}"

    companion object {
        private const val PREFIX = "org.radarcns.android."
        const val SERVER_STATUS_CHANGED = PREFIX + "ServerStatusListener.Status"
        const val SERVER_RECORDS_SENT_TOPIC = PREFIX + "ServerStatusListener.topic"
        const val SERVER_RECORDS_SENT_NUMBER = PREFIX + "ServerStatusListener.lastNumberOfRecordsSent"
        const val CACHE_TOPIC = PREFIX + "DataCache.topic"
        const val CACHE_RECORDS_UNSENT_NUMBER = PREFIX + "DataCache.numberOfRecords.first"
        const val SOURCE_SERVICE_CLASS = PREFIX + "SourceService.getClass"
        const val SOURCE_STATUS_CHANGED = PREFIX + "SourceStatusListener.Status"
        const val SOURCE_STATUS_NAME = PREFIX + "SourceManager.getName"
        const val SOURCE_CONNECT_FAILED = PREFIX + "SourceStatusListener.sourceFailedToConnect"

        private val logger = LoggerFactory.getLogger(SourceService::class.java)
    }
}
