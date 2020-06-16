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

package org.radarbase.android.auth

import androidx.annotation.Keep

/**
 * Created by joris on 19/06/2017.
 */

@Keep
interface LoginListener {
    /** Callback for when a login succeeds.  */
    fun loginSucceeded(manager: LoginManager?, authState: AppAuthState)

    /**
     * Callback for when a login fails. Expected exceptions are
     *
     *  * [org.radarcns.producer.AuthenticationException] if a login attempt was made but not
     * accepted by the server,
     *  * [com.google.firebase.remoteconfig.FirebaseRemoteConfigException] if the
     * configuration was incorrect,
     *  * [java.net.ConnectException] if no network connection was available, or
     *  * [java.io.IOException] if a generic I/O exception occurred.
     *
     *
     * It my also be `null`.
     */
    fun loginFailed(manager: LoginManager?, ex: Exception?)
}
