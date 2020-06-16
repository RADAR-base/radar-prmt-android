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

import org.radarbase.android.auth.SourceMetadata
import java.io.Closeable
import java.io.IOException

/**
 *  Manager of a data source.
 * This should start connect to the source, start data collection and
 * stop data location.
 */
interface SourceManager<T> : Closeable {
    /** Whether the source manager was already closed.  */
    val isClosed: Boolean

    /**
     * Get the state of a source.
     *
     * If no source is connected, it returns a state with [SourceStatusListener.Status.DISCONNECTED]
     * status.
     * @return source state
     */
    val state: T

    /**
     * Get the name of a connected source.
     */
    val name: String

    /**
     * Start scanning and try to connect. Check that [.isClosed] is false before calling
     * this.
     * @param acceptableIds IDs that are acceptable to connect to. If empty, no selection is made.
     */
    fun start(acceptableIds: Set<String>)

    /**
     * Close the source manager. After calling this, [.start] may no longer be called.
     */
    @Throws(IOException::class)
    override fun close()

    /**
     * Called when a source registration succeeds.
     * @param source source metadata
     */
    fun didRegister(source: SourceMetadata)
}
