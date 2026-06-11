/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// WCAG 2.1 contrast ratio between two opaque sRGB colors. no Android deps
// so it runs in JVM tests; used by RadixPaletteContrastTest.
internal object WcagContrast {
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun ratio(argbA: Int, argbB: Int): Double {
        val la = relativeLuminance(argbA)
        val lb = relativeLuminance(argbB)
        val lighter = max(la, lb)
        val darker = min(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    // black or white, whichever reads with more contrast on the given opaque
    // background. the max always clears ~4.58:1 (the worst case sits near a
    // mid-luminance fill), so this replaces luminance-midpoint foreground guesses.
    fun onColor(backgroundArgb: Int): Int =
        if (ratio(backgroundArgb, BLACK) >= ratio(backgroundArgb, WHITE)) BLACK else WHITE

    private fun relativeLuminance(argb: Int): Double {
        val r = channelToLinear((argb shr 16) and 0xFF)
        val g = channelToLinear((argb shr 8) and 0xFF)
        val b = channelToLinear(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channelToLinear(channel: Int): Double {
        val srgb = channel / 255.0
        return if (srgb <= 0.03928) {
            srgb / 12.92
        } else {
            ((srgb + 0.055) / 1.055).pow(2.4)
        }
    }
}
