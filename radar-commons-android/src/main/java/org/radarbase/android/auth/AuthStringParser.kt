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
import org.radarbase.android.util.Parser
import java.io.IOException

/** AuthStringProcessor to parse a string with some form of authentication and parse it to a
 * proper state.  */
@Keep
interface AuthStringParser : Parser<String, AppAuthState> {
    /**
     * Parse an authentication state from a string.
     * @param value string that contains some form of identification.
     * @return authentication state or `null` if the authentication was passed for further
     * external processing.
     * @throws IllegalArgumentException if the string is not a valid authentication string
     */
    @Throws(IOException::class)
    override fun parse(value: String): AppAuthState
}
