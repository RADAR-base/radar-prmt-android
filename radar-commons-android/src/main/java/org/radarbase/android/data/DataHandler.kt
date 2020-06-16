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

package org.radarbase.android.data

import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import org.radarbase.android.kafka.KafkaDataSubmitter
import org.radarbase.android.kafka.ServerStatusListener
import org.radarbase.android.util.BatteryStageReceiver
import org.radarbase.config.ServerConfig
import org.radarbase.producer.rest.SchemaRetriever
import org.radarbase.topic.AvroTopic

interface DataHandler<K, V> : ServerStatusListener {
    /** Get all caches.  */
    val caches: List<ReadableDataCache>

    /** Get caches currently active for sending.  */
    val activeCaches: List<DataCacheGroup<*, *>>

    val recordsSent: Map<String, Long>
    val status: ServerStatusListener.Status

    fun <W: V> registerCache(topic: AvroTopic<K, W>): DataCache<K, W>

    fun handler(build: DataHandlerConfiguration.() -> Unit)
    fun getCache(topic: String): DataCache<*, *>

    data class RestConfiguration(
            /** Request headers. */
            var headers: Headers = headersOf(),
            /** Kafka server configuration. If null, no rest sender will be configured. */
            var kafkaConfig: ServerConfig? = null,
            /** Schema registry retriever. */
            var schemaRetriever: SchemaRetriever? = null,
            /** Connection timeout in seconds. */
            var connectionTimeout: Long = SENDER_CONNECTION_TIMEOUT_DEFAULT,
            /** Whether to try to use GZIP compression in requests. */
            var useCompression: Boolean = false,
            /** Whether to try to use binary encoding in request. */
            var hasBinaryContent: Boolean = false)

    data class DataHandlerConfiguration(
            /** Minimum battery level to send data. */
            var batteryStageLevels: BatteryStageReceiver.StageLevels =
                    BatteryStageReceiver.StageLevels(MINIMUM_BATTERY_LEVEL, REDUCED_BATTERY_LEVEL),
            /** Data sending reduction if a reduced battery level is detected. */
            var reducedUploadMultiplier: Int = 5,
            /** Whether to send only if Wifi or Ethernet is enabled. */
            var sendOnlyWithWifi: Boolean = true,
            /** Whether to use cellular only for high priority topics. */
            var sendOverDataHighPriority: Boolean = true,
            /** Topics marked as high priority. */
            var highPriorityTopics: Set<String> = emptySet(),
            var restConfig: RestConfiguration = RestConfiguration(),
            var cacheConfig: DataCache.CacheConfiguration = DataCache.CacheConfiguration(),
            var submitterConfig: KafkaDataSubmitter.SubmitterConfiguration = KafkaDataSubmitter.SubmitterConfiguration()
    ) {
        fun batteryLevel(builder: BatteryStageReceiver.StageLevels.() -> Unit) {
            batteryStageLevels = batteryStageLevels.copy().apply(builder)
        }
        fun cache(builder: DataCache.CacheConfiguration.() -> Unit) {
            cacheConfig = cacheConfig.copy().apply(builder)
        }
        fun submitter(builder: KafkaDataSubmitter.SubmitterConfiguration.() -> Unit) {
            submitterConfig = submitterConfig.copy().apply(builder)
        }
        fun rest(builder: RestConfiguration.() -> Unit) {
            restConfig = restConfig.copy().apply(builder)
        }
    }

    companion object {
        private const val SENDER_CONNECTION_TIMEOUT_DEFAULT = 10L
        private const val MINIMUM_BATTERY_LEVEL = 0.1f
        private const val REDUCED_BATTERY_LEVEL = 0.2f
    }
}
