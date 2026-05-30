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

// Okabe-Ito 8-color color-blind-safe palette (Nature Methods 2011, "Wong").
// Used for account avatars and the local-calendar color picker. A neutral
// grey replaces Okabe-Ito's pure black, which renders poorly as a chip
// background against dark themes.
//
// Yellow (#F0E442) does not clear WCAG 1.4.11 (3:1) against a white
// surface; that's an accepted color-blind-safety tradeoff and the reason
// RadixPaletteContrastTest exempts this palette. If you need a swatch
// set that's also contrast-safe, pick Radix from the Settings switcher.
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

// Radix Colors step 9 ("solid" step), hue-ordered. Step 9 is the
// "solid background for white text" step in Radix; for our use case
// (chip stripe / drawer dot on a neutral surface) we require each
// swatch to clear 3:1 against the base surface in at least one theme
// mode, not both. RadixPaletteContrastTest enforces that bar. Some
// swatches read best in light mode (Indigo, Violet); some in dark
// (Orange, Amber, Slate); a few work in both (Tomato, Pink, Crimson).
// Users pick the colors that suit their mode.
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

// Set of palettes the recolor pickers can offer. Adding a third palette
// is one entry plus one swatch-list constant. `OkabeIto` is the default
// so existing installs see no visual change.
enum class PaletteId(val swatches: List<Color>, val labelRes: Int) {
    OkabeIto(OkabeItoPalette, R.string.palette_okabe_ito),
    Radix(RadixSolidPalette, R.string.palette_radix),
}
