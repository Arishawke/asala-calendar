/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class WcagContrastTest {
    // WCAG 2.1's headline fixture: pure black on pure white is the maximum
    // achievable contrast ratio of 21:1.
    @Test
    fun `black on white is 21 to 1`() {
        val ratio = WcagContrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        assertEquals(21.0, ratio, 0.01)
    }

    @Test
    fun `identical colors are 1 to 1`() {
        val ratio = WcagContrast.ratio(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt())
        assertEquals(1.0, ratio, 1e-9)
    }

    // 0x767676 against white is the documented "minimum AA text contrast"
    // benchmark; clears 4.5:1 by a hair.
    @Test
    fun `767676 on white clears AA contrast minimum`() {
        val ratio = WcagContrast.ratio(0xFF767676.toInt(), 0xFFFFFFFF.toInt())
        assertEquals(4.54, ratio, 0.05)
    }

    @Test
    fun `ratio is symmetric in its arguments`() {
        val ab = WcagContrast.ratio(0xFFE54D2E.toInt(), 0xFF211F26.toInt())
        val ba = WcagContrast.ratio(0xFF211F26.toInt(), 0xFFE54D2E.toInt())
        assertEquals(ab, ba, 1e-9)
    }

    // Alpha is ignored: the helper assumes opaque colors and only reads the
    // RGB channels, so two colors that differ only in alpha return 1:1.
    @Test
    fun `alpha is ignored`() {
        val ratio = WcagContrast.ratio(0x00FFFFFF, 0xFFFFFFFF.toInt())
        assertEquals(1.0, ratio, 1e-9)
    }
}
