package org.radarbase.android.auth.portal

import org.junit.Assert.*
import org.junit.Test
import org.radarbase.android.auth.AppAuthState
import org.radarbase.android.auth.LoginManager.Companion.AUTH_TYPE_BEARER
import org.radarbase.android.auth.portal.ManagementPortalClient.Companion.MP_REFRESH_TOKEN_PROPERTY
import java.util.concurrent.TimeUnit

class AccessTokenParserTest {
    @Test
    @Throws(Exception::class)
    fun parse() {
        val parser = AccessTokenParser(AppAuthState())

        val parsedState = parser.parse(
                "{\"access_token\":\"abcd\","
                        + "\"sub\":\"u\","
                        + "\"refresh_token\":\"efgh\","
                        + "\"expires_in\":10}").alter {
            isPrivacyPolicyAccepted = true
        }

        assertEquals("abcd", parsedState.token)
        assertEquals("efgh", parsedState.getAttribute(MP_REFRESH_TOKEN_PROPERTY))
        assertEquals("u", parsedState.userId)
        assertTrue(parsedState.isValidFor(9, TimeUnit.SECONDS))
        assertFalse(parsedState.isValidFor(11, TimeUnit.SECONDS))
        assertEquals(AUTH_TYPE_BEARER, parsedState.tokenType)
        assertEquals("Bearer abcd", parsedState.headers[0].value)
    }
}
