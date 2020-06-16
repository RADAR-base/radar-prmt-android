package org.radarbase.android.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.radarbase.android.auth.LoginManager.Companion.AUTH_TYPE_BEARER
import org.radarbase.android.auth.portal.ManagementPortalClient.Companion.MP_REFRESH_TOKEN_PROPERTY
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AppAuthStateTest {
    private lateinit var state: AppAuthState
    private lateinit var sources: List<SourceMetadata>

    @Before
    fun setUp() {
        sources = listOf(SourceMetadata().apply {
            type = SourceType(1, "radar", "test", "1.0", true)
            expectedSourceName = "something"
        })

        state = AppAuthState {
            token = "abcd"
            tokenType = AUTH_TYPE_BEARER
            projectId = "p"
            userId = "u"
            attributes[MP_REFRESH_TOKEN_PROPERTY] = "efgh"
            sourceMetadata += sources
            addHeader("Authorization", "Bearer abcd")
            expiration = System.currentTimeMillis() + 10_000L
            isPrivacyPolicyAccepted = true
        }

        testProperties(state)
    }

    @Test
    fun addToPreferences() {
        val readState = ApplicationProvider.getApplicationContext<Context>().let { context ->
            state.addToPreferences(context)
            AppAuthState.from(context)
        }

        testProperties(readState)
    }

    private fun testProperties(state: AppAuthState, refreshToken: String = "efgh") {
        assertEquals("abcd", state.token)
        assertEquals(refreshToken, state.getAttribute(MP_REFRESH_TOKEN_PROPERTY))
        assertEquals("p", state.projectId)
        assertEquals("u", state.userId)
        assertTrue(state.isValidFor(9, TimeUnit.SECONDS))
        assertFalse(state.isValidFor(11, TimeUnit.SECONDS))
        assertEquals(AUTH_TYPE_BEARER.toLong(), state.tokenType.toLong())
        assertEquals("Bearer abcd", state.headers[0].value)
        assertEquals(sources, state.sourceMetadata)
    }

    @Test
    fun newBuilder() {
        val builtState = state.alter {
            attributes[MP_REFRESH_TOKEN_PROPERTY] = "else"
        }

        testProperties(builtState, "else")
        testProperties(state)
    }
}
