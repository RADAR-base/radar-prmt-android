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

package org.radarbase.android.kafka

import org.radarbase.android.util.SafeHandler
import org.radarbase.producer.AuthenticationException
import org.radarbase.producer.KafkaSender
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks the connection of a sender. It does so using two mechanisms: a regular
 * heartbeatInterval signal when the connection is assumed to be
 * present, and a exponential back-off mechanism if the connection is severed. If the connection is
 * assessed to be present through another mechanism, [.didConnect] should be called,
 * conversely, if it is assessed to be severed, [.didDisconnect] should be
 * called.
 */
internal class KafkaConnectionChecker(private val sender: KafkaSender,
                                      private val mHandler: SafeHandler,
                                      private val listener: ServerStatusListener,
                                      heartbeatSecondsInterval: Long) {
    private val isConnectedBacking: AtomicBoolean = AtomicBoolean(true)
    private lateinit var random: Random
    private var future: SafeHandler.HandlerFuture? = null
    private val heartbeatInterval: Long = heartbeatSecondsInterval * 1000L
    private var lastConnection: Long = -1L
    private var retries: Int = 0

    val isConnected: Boolean
        get() = isConnectedBacking.get()

    init {
        mHandler.execute { random = Random() }
    }

    /**
     * Check whether the connection was closed and try to reconnect.
     */
    private fun makeCheck() {
        future = null
        try {
            if (!isConnected) {
                if (sender.resetConnection()) {
                    didConnect()
                    listener.updateServerStatus(ServerStatusListener.Status.CONNECTED)
                    logger.info("Sender reconnected")
                } else {
                    retry()
                }
            } else if (System.currentTimeMillis() - lastConnection > 15_000L) {
                if (sender.isConnected) {
                    didConnect()
                } else {
                    didDisconnect(null)
                }
            } else {
                post(heartbeatInterval)
            }
        } catch (ex: AuthenticationException) {
            didDisconnect(ex)
        }
    }

    private fun post(delay: Long) {
        mHandler.executeReentrant {
            future?.cancel()
            future = mHandler.delay(delay, ::makeCheck)
        }
    }

    /** Check the connection as soon as possible.  */
    fun check() {
        post(0)
    }

    /** Retry the connection with an incremental backoff.  */
    private fun retry() {
        mHandler.executeReentrant {
            val sample = random.nextDouble()
            retries++
            val range = Math.min(INCREMENTAL_BACKOFF_MILLISECONDS * (1 shl retries - 1),
                    MAX_BACKOFF_MILLISECONDS)
            val nextWait = Math.round(sample * range.toDouble() * 1000.0)
            post(nextWait)
        }
    }

    /** Signal that the sender successfully connected.  */
    fun didConnect() {
        mHandler.executeReentrant {
            lastConnection = System.currentTimeMillis()
            isConnectedBacking.set(true)
            post(heartbeatInterval)
            retries = 0
        }
    }

    /**
     * Signal that the Kafka REST sender has disconnected.
     * @param ex exception the sender disconnected with, may be null
     */
    fun didDisconnect(ex: Exception?) {
        logger.warn("Sender is disconnected", ex)

        if (isConnectedBacking.compareAndSet(true, false)) {
            if (ex is AuthenticationException) {
                logger.warn("Failed to authenticate to server: {}", ex.message)
                listener.updateServerStatus(ServerStatusListener.Status.UNAUTHORIZED)
            } else {
                listener.updateServerStatus(ServerStatusListener.Status.DISCONNECTED)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KafkaConnectionChecker::class.java)

        private const val INCREMENTAL_BACKOFF_MILLISECONDS = 60000
        private const val MAX_BACKOFF_MILLISECONDS = 14400000 // 4 hours
    }
}
