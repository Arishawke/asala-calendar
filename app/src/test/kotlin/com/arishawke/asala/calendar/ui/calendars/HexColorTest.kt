/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexColorTest {
    @Test
    fun `parses six-digit hex with leading hash`() {
        assertEquals(0xFFFF0000.toInt(), HexColor.parse("#FF0000"))
    }

    @Test
    fun `parses six-digit hex without hash, case-insensitive`() {
        assertEquals(0xFF00FF00.toInt(), HexColor.parse("00ff00"))
    }

    @Test
    fun `expands three-digit shorthand`() {
        assertEquals(0xFFAABBCC.toInt(), HexColor.parse("#abc"))
    }

    // No alpha in the input; the result must be fully opaque so chips
    // never render half-transparent.
    @Test
    fun `forces alpha opaque`() {
        assertEquals(0xFFFFFFFF.toInt(), HexColor.parse("FFFFFF"))
    }

    @Test
    fun `rejects wrong length`() {
        assertNull(HexColor.parse("#FF00"))
    }

    @Test
    fun `rejects non-hex characters`() {
        assertNull(HexColor.parse("ZZZZZZ"))
    }

    @Test
    fun `rejects empty input`() {
        assertNull(HexColor.parse(""))
    }

    // Display drops the alpha byte and uppercases.
    @Test
    fun `format strips alpha`() {
        assertEquals("#112233", HexColor.format(0x80112233.toInt()))
    }
}
