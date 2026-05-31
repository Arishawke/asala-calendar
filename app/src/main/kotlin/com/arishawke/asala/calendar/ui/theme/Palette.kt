/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.theme

import androidx.compose.ui.graphics.Color
import com.arishawke.asala.calendar.R

// Okabe-Ito color-blind-safe palette (Wong, Nature Methods 2011). grey
// substitutes Okabe-Ito black, which reads poorly as a chip on dark.
//
// yellow (#F0E442) misses WCAG 1.4.11 (3:1) on white; an accepted
// color-blind-safety tradeoff and why RadixPaletteContrastTest exempts
// this palette. pick Radix in Settings for a contrast-safe set.
internal val OkabeItoPalette: List<Color> =
    listOf(
        Color(0xFF999999), // grey (substituted for Okabe-Ito black)
        Color(0xFFE69F00), // orange
        Color(0xFF56B4E9), // sky blue
        Color(0xFF009E73), // bluish green
        Color(0xFFF0E442), // yellow
        Color(0xFF0072B2), // blue
        Color(0xFFD55E00), // vermillion
        Color(0xFFCC79A7), // reddish purple
    )

// Radix step 9 ("solid"), hue-ordered. for chip stripe / drawer dot on a
// neutral surface each swatch need only clear 3:1 in one theme mode, not
// both (RadixPaletteContrastTest enforces this). per-swatch mode noted below.
internal val RadixSolidPalette: List<Color> =
    listOf(
        Color(0xFFE54D2E), // tomato 9, warm red (both modes)
        Color(0xFFF76B15), // orange 9, warmth slot (dark)
        Color(0xFFFFB224), // amber 9, sunny accent (dark)
        Color(0xFF46A758), // grass 9, natural / personal (dark)
        Color(0xFF12A594), // teal 9, distinct from blue and green (dark)
        Color(0xFF05A2C2), // cyan 9, sky accent (dark)
        Color(0xFF3E63DD), // indigo 9, default-blue slot (light)
        Color(0xFF6E56CF), // violet 9, separator (light)
        Color(0xFFD6409F), // pink 9, high-saturation magenta (both)
        Color(0xFFE93D82), // crimson 9, softer pink-red (both)
        Color(0xFF687076), // slate 9, neutral / no-category (dark)
    )

// palettes the recolor pickers offer. OkabeIto default so existing
// installs see no visual change.
enum class PaletteId(val swatches: List<Color>, val labelRes: Int) {
    OkabeIto(OkabeItoPalette, R.string.palette_okabe_ito),
    Radix(RadixSolidPalette, R.string.palette_radix),
}
