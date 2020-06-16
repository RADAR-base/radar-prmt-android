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

package org.radarbase.android.util

import android.content.SharedPreferences
import android.util.Base64
import org.radarbase.util.Serialization
import java.nio.ByteBuffer
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Hash generator that uses the HmacSHA256 algorithm to hash data. This algorithm ensures that
 * it is very very hard to guess what data went in. The fixed key to the algorithm is stored in
 * the SharedPreferences. As long as the key remains there, a given input string will always
 * return the same output hash.
 *
 *
 * HashGenerator must be used from a single thread or synchronized externally.
 * This persists the hash.key property in the given preferences.
 */
class HashGenerator(private val preferences: SharedPreferences) {
    private val sha256: Mac
    private val hashBuffer = ByteArray(4)

    init {
        try {
            this.sha256 = Mac.getInstance("HmacSHA256")
            sha256.init(SecretKeySpec(loadHashKey(), "HmacSHA256"))
        } catch (ex: NoSuchAlgorithmException) {
            throw IllegalStateException("Cannot retrieve hashing algorithm", ex)
        } catch (ex: InvalidKeyException) {
            throw IllegalStateException("Encoding is invalid", ex)
        }

    }

    private fun loadHashKey(): ByteArray {
        var b64Salt = preferences.getString(HASH_KEY, null)
        return b64Salt?.let { Base64.decode(it, Base64.NO_WRAP) }
                ?: ByteArray(16).also { byteSalt ->
                    SecureRandom().nextBytes(byteSalt)

                    b64Salt = Base64.encodeToString(byteSalt, Base64.NO_WRAP)
                    preferences.edit().putString(HASH_KEY, b64Salt).apply()
                }
    }

    /** Create a unique hash for a given target.  */
    fun createHash(target: Int): ByteArray {
        Serialization.intToBytes(target, hashBuffer, 0)
        return sha256.doFinal(hashBuffer)
    }

    /** Create a unique hash for a given target.  */
    fun createHash(target: String): ByteArray = sha256.doFinal(target.toByteArray())

    /**
     * Create a unique hash for a given target. Internally this calls
     * [.createHash].
     */
    fun createHashByteBuffer(target: Int): ByteBuffer = ByteBuffer.wrap(createHash(target))

    /**
     * Create a unique hash for a given target. Internally this calls
     * [.createHash].
     */
    fun createHashByteBuffer(target: String): ByteBuffer = ByteBuffer.wrap(createHash(target))

    companion object {
        private const val HASH_KEY = "hash.key"
    }
}
