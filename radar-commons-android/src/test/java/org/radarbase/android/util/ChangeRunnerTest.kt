package org.radarbase.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ChangeRunnerTest {
    @Test
    fun withInit() {
        val runner = ChangeRunner("")
        assertEquals("", runner.lastResult)
        assertEquals("", runner.value)
        assertEquals("", runner.applyIfChanged("") {
            assertTrue(false)
        })
        assertEquals("", runner.lastResult)
        assertEquals("", runner.value)
        assertEquals("new", runner.applyIfChanged("new") {
            assertTrue(true)
        })
        assertEquals("new", runner.applyIfChanged("new") {
            assertTrue(false)
        })
    }

    @Test
    fun withoutInit() {
        val runner = ChangeRunner<String>()
        assertEquals("", runner.applyIfChanged(""))
        assertEquals("", runner.lastResult)
        assertEquals("", runner.value)
        assertEquals("new", runner.applyIfChanged("new") {
            assertTrue(true)
        })
        assertEquals("new", runner.applyIfChanged("new") {
            assertTrue(false)
        })
    }

    @Test(expected = IllegalStateException::class)
    fun withoutInitFailValue() {
        val applier = ChangeRunner<String>()
        applier.value
    }

    @Test(expected = UninitializedPropertyAccessException::class)
    fun withoutInitFailResult() {
        val applier = ChangeRunner<String>()
        applier.lastResult
    }
}
