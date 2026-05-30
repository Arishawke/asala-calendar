/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Opacity used to fade past dates across Month and Week views when the
// user has enabled "Dim past dates" in Settings.
internal const val PastDateAlpha: Float = 0.45f

// Calendar-specific semantic color roles, layered on top of Material 3's
// scheme. Each token names the meaning of the color (todayHighlight, nowLine)
// rather than its hue, so swapping the source later only changes this file.
object CalendarTokens {

    val todayHighlight: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    val onTodayHighlight: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onPrimary

    // Monochrome on purpose: the line should read as ink against the
    // surface, light or dark, rather than the loud accent color it was
    // before. onSurface flips with theme.
    val nowLine: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurface
}
