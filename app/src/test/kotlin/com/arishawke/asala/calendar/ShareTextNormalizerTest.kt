/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareTextNormalizerTest {
    @Test
    fun `null input normalizes to null`() {
        assertNull(ShareTextNormalizer.normalize(null))
    }

    @Test
    fun `blank input normalizes to null`() {
        assertNull(ShareTextNormalizer.normalize("   "))
    }

    @Test
    fun `newlines and tabs collapse to single spaces`() {
        assertEquals(
            "Dinner with Sam Friday 7pm",
            ShareTextNormalizer.normalize("Dinner\nwith\tSam  Friday\n\n7pm"),
        )
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        assertEquals("call mom", ShareTextNormalizer.normalize("  call mom  \n"))
    }

    @Test
    fun `input over 500 characters is capped`() {
        val long = "a".repeat(600)
        val result = ShareTextNormalizer.normalize(long)
        assertEquals(500, result?.length)
        assertEquals("a".repeat(500), result)
    }

    @Test
    fun `input at exactly 500 characters is unchanged`() {
        val exact = "b".repeat(500)
        assertEquals(exact, ShareTextNormalizer.normalize(exact))
    }
}
