/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

// Build-time guard for the curated Radix palette. Asserts every swatch
// clears WCAG 2.1 criterion 1.4.11 (>=3:1 non-text UI contrast) against
// the base surface in at least ONE theme mode. The "options, not
// policing" framing: users pick a mode and then pick colors that work
// for that mode; an amber that washes out in light mode is fine as a
// dark-mode-friendly option.
//
// A swatch that fails BOTH modes is broken. The 4.5:1 text criterion
// (1.4.3) does not apply: chip text uses `onSurface` (Material 3
// managed), not the swatch.
//
// Base surfaces match Theme.kt: `lightColorScheme().surface` =
// `#FFFBFE`, dark = `withLiftedDarkSurfaces` = `#211F26`. AMOLED is
// strictly darker than dark, so passing dark implies passing AMOLED.
class RadixPaletteContrastTest {
    private val lightBase = 0xFFFFFBFE.toInt()
    private val darkBase = 0xFF211F26.toInt()
    private val minRatio = 3.0

    @Test
    fun `every Radix swatch is visible in at least one theme mode`() {
        val failures = mutableListOf<String>()
        for (swatch in RadixSolidPalette) {
            val swatchArgb = swatch.toArgb()
            val lightRatio = WcagContrast.ratio(swatchArgb, lightBase)
            val darkRatio = WcagContrast.ratio(swatchArgb, darkBase)
            if (lightRatio < minRatio && darkRatio < minRatio) {
                failures += "swatch=%06X light=%.2f dark=%.2f"
                    .format(swatchArgb and 0xFFFFFF, lightRatio, darkRatio)
            }
        }
        assertTrue(
            "Radix palette swatches that fail 3:1 in both modes:\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }
}
