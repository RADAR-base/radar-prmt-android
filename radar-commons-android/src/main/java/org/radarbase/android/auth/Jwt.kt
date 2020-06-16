package org.radarbase.android.auth

import android.util.Base64
import android.util.Base64.NO_PADDING
import android.util.Base64.NO_WRAP
import org.json.JSONException
import org.json.JSONObject

class Jwt(val originalString: String, val header: JSONObject, val body: JSONObject) {
    companion object {
        private val jwtSeparatorCharacter = "\\.".toRegex()

        @Throws(JSONException::class)
        fun parse(token: String): Jwt {
            val parts = token.split(jwtSeparatorCharacter)
            require(parts.size == 3) { "Argument is not a valid JSON web token. Need 3 parts but got " + parts.size }
            val header = String(Base64.decode(parts[0], NO_PADDING or NO_WRAP))
                    .let(::JSONObject)

            val body = String(Base64.decode(parts[1], NO_PADDING or NO_WRAP))
                    .let(::JSONObject)

            return Jwt(token, header, body)
        }
    }
}
