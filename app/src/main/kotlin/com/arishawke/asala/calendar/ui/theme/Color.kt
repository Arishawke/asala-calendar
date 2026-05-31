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

// fade for past dates when "Dim past dates" is on.
internal const val PastDateAlpha: Float = 0.45f

// semantic color roles over the M3 scheme. tokens name meaning not hue, so
// swapping the source later touches only this file.
object CalendarTokens {

    val todayHighlight: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    val onTodayHighlight: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onPrimary

    // monochrome on purpose: reads as ink against the surface in either
    // theme, not the loud accent it used to be. onSurface flips with theme.
    val nowLine: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurface
}
