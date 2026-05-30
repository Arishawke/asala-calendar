/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalIs24Hour: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

private val FallbackLightColors = lightColorScheme()
private val FallbackDarkColors = darkColorScheme()

// Material 3's default dark surface (tone 6, ~#141218) reads as near-black
// on most devices. Lift the surface family by ~6 tones so dark mode feels
// like a proper dark grey, leaving pure-black for a future AMOLED option.
// Accent roles (primary / secondary / tertiary) stay untouched so dynamic
// color still pulls personality from the wallpaper on Android 12+.
private fun ColorScheme.withLiftedDarkSurfaces(): ColorScheme = copy(
    surface = Color(0xFF211F26),
    background = Color(0xFF211F26),
    surfaceContainerLowest = Color(0xFF1A181E),
    surfaceContainerLow = Color(0xFF26242A),
    surfaceContainer = Color(0xFF2A282F),
    surfaceContainerHigh = Color(0xFF353339),
    surfaceContainerHighest = Color(0xFF403E44),
)

// AMOLED variant: copy the dark scheme but force every surface tone to
// pure black so OLED panels can power-gate pixels. Accent roles stay so
// dynamic color still pulls personality from the wallpaper.
private fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    surface = Color.Black,
    background = Color.Black,
    surfaceVariant = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
)

@Composable
fun AsalaCalendarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val canUseDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val colorScheme = when {
        canUseDynamic && amoled -> dynamicDarkColorScheme(context).withAmoledSurfaces()
        canUseDynamic && darkTheme -> dynamicDarkColorScheme(context).withLiftedDarkSurfaces()
        canUseDynamic && !darkTheme -> dynamicLightColorScheme(context)
        amoled -> FallbackDarkColors.withAmoledSurfaces()
        darkTheme -> FallbackDarkColors.withLiftedDarkSurfaces()
        else -> FallbackLightColors
    }

    // Keep the system status / nav bar icon contrast in sync with the
    // resolved theme, so a manual Light override on a dark-system phone
    // still gets dark icons on the status bar.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            val effectiveDark = darkTheme || amoled
            controller.isAppearanceLightStatusBars = !effectiveDark
            controller.isAppearanceLightNavigationBars = !effectiveDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AsalaTypography,
        content = content,
    )
}
