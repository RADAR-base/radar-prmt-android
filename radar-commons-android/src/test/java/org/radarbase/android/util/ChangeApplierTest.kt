package org.radarbase.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ChangeApplierTest {
    @Test
    fun withInit() {
        val applier = ChangeApplier("", { t -> "$t.applied"})
        assertEquals(".applied", applier.lastResult)
        assertEquals("", applier.value)
        assertEquals(".applied", applier.applyIfChanged("") {
            assertTrue(false)
        })
        assertEquals(".applied", applier.lastResult)
        assertEquals("", applier.value)
        assertEquals("new.applied", applier.applyIfChanged("new") {
            assertTrue(true)
        })
        assertEquals("new.applied", applier.applyIfChanged("new") {
            assertTrue(false)
        })
    }

    @Test
    fun withoutInit() {
        val applier = ChangeApplier<String, String>({ t -> "$t.applied"})
        assertEquals(".applied", applier.applyIfChanged(""))
        assertEquals(".applied", applier.lastResult)
        assertEquals("", applier.value)
        assertEquals("new.applied", applier.applyIfChanged("new") {
            assertTrue(true)
        })
        assertEquals("new.applied", applier.applyIfChanged("new") {
            assertTrue(false)
        })
    }

    @Test(expected = IllegalStateException::class)
    fun withoutInitFailValue() {
        val applier = ChangeApplier<String, String>({ t -> "$t.applied"})
        applier.value
    }

    @Test(expected = UninitializedPropertyAccessException::class)
    fun withoutInitFailResult() {
        val applier = ChangeApplier<String, String>({ t -> "$t.applied"})
        applier.lastResult
    }
}
