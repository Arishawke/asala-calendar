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

// M3 Typography overlay on the device default font. tightens title line-
// heights so titles read as titles; other slots fall through to M3.
//
// no custom typeface: a bundled .ttf is brand identity, and Downloadable
// Fonts phone home and need Play Services, both out for an offline-first
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
