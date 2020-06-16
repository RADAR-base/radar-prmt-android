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

/** Listen for updates of sources.  */
interface SourceStatusListener {
    enum class Status {
        /** A source is found and the source manager is trying to connect to it. No data is yet received.  */
        CONNECTING,
        /** A source is currently disconnecting. It may still stream data. */
        DISCONNECTING,
        /** A source was disconnected and will no longer stream data. If this status is passed without an argument, the source manager is no longer active.  */
        DISCONNECTED,
        /** A compatible source was found and connected to. Data can now stream in.  */
        CONNECTED,
        /** A source manager is scanning for compatible sources. This status is passed without an argument.  */
        READY
    }

    /**
     * A source has an updated status.
     *
     * If the status concerns the entire system state, null is
     * passed as sourceManager.
     */
    fun sourceStatusUpdated(manager: SourceManager<*>, status: Status)

    /**
     * A source was found but it was not compatible.
     *
     * No further action is required, but the user can be informed that the connection has failed.
     * @param name human-readable source name.
     */
    fun sourceFailedToConnect(name: String)
}
