/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EscapeLikePatternTest {
    // LIKE metacharacters must be escaped with the ESCAPE '\' char so a search
    // for "50%" matches the literal, not "any suffix".
    @Test
    fun `escapes percent underscore and backslash`() {
        assertEquals("""50\% off""", escapeLikePattern("50% off"))
        assertEquals("""a\_b""", escapeLikePattern("a_b"))
        assertEquals("""x\\y""", escapeLikePattern("""x\y"""))
    }

    @Test
    fun `escapes a run of only metacharacters`() {
        assertEquals("""\%\_\\""", escapeLikePattern("""%_\"""))
    }

    @Test
    fun `passes ordinary text through unchanged`() {
        assertEquals("dentist", escapeLikePattern("dentist"))
        assertEquals("", escapeLikePattern(""))
    }
}
