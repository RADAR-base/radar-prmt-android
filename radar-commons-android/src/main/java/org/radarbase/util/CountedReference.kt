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

package org.radarbase.util

/**
 * Reference that will close itself once its released.
 */
class CountedReference<T>(private val creator: () -> T, private val destroyer: T.() -> Unit) {
    private var value: T? = null
    private var count: Int = 0

    init {
        value = null
        count = 0
    }

    @Synchronized
    fun acquire(): T {
        if (count == 0) {
            value = creator()
        }
        count++
        return value!!
    }

    @Synchronized
    fun release() {
        check(count > 0) { "Cannot release object that was not acquired" }
        count--
        if (count == 0) {
            value?.let {
                it.destroyer()
                value = null
            }
        }
    }
}
