/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// M3 Typography overlay. Stays on the device default font (Roboto on
// most Android) and the M3 type scale. Tightens title line-heights
// slightly so titles read as titles rather than as oversized body text.
// All other slots fall through to M3 defaults.
//
// Why no custom typeface: bundled .ttf is a brand-identity move, and
// Downloadable Fonts via the GMS provider phones home on first request
// plus requires Play Services, both disqualified for an offline-first
// GPLv3 app.
val AsalaTypography: Typography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 22.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 27.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
)
