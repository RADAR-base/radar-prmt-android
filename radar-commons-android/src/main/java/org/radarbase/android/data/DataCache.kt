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

import org.radarbase.topic.AvroTopic

import java.io.Flushable

interface DataCache<K, V> : Flushable, ReadableDataCache {
    /** Get the topic the cache stores.  */
    val topic: AvroTopic<K, V>

    /** Add a new measurement to the cache.  */
    fun addMeasurement(key: K?, value: V?)

    /** Configuration. */
    var config: CacheConfiguration

    data class CacheConfiguration(
            /** Time in milliseconds until data is committed to disk. */
            var commitRate: Long = 10_000L,
            /** Maximum size the data cache may have in bytes.  */
            var maximumSize: Long = 450_000_000
    )
}
