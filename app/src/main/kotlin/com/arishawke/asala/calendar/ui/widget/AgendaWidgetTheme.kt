/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.arishawke.asala.calendar.ui.settings.ThemeMode
import com.arishawke.asala.calendar.ui.settings.WidgetThemeMode

// neutral surfaces/text that read as Asala. event accent comes from each row's
// own color, so no palette is needed here.
data class AgendaWidgetColors(
    val background: ColorProvider,
    val onBackground: ColorProvider,
    val secondary: ColorProvider,
)

enum class ResolvedTheme { Light, Dark, Amoled }

private const val LIGHT_BACKGROUND = 0xFFFFFFFFL
private const val LIGHT_ON = 0xFF1A1C1EL
private const val LIGHT_SECONDARY = 0xFF5A6068L
private const val DARK_BACKGROUND = 0xFF14181FL
private const val AMOLED_BACKGROUND = 0xFF000000L
private const val DARK_ON = 0xFFE7E9EEL
private const val DARK_SECONDARY = 0xFF9AA3B2L

// fixed translucent opacity: a legible floor over a typical wallpaper. surface
// only; text and event colors stay opaque so they remain readable.
private const val TRANSLUCENT_BACKGROUND_ALPHA = 0.7f

object AgendaWidgetTheme {
    // resolve System to the concrete look the widget paints; explicit modes
    // ignore the system setting, mirroring the in-app theme override.
    fun resolve(themeMode: ThemeMode, systemInDark: Boolean): ResolvedTheme = when (themeMode) {
        ThemeMode.Light -> ResolvedTheme.Light
        ThemeMode.Dark -> ResolvedTheme.Dark
        ThemeMode.Amoled -> ResolvedTheme.Amoled
        ThemeMode.System -> if (systemInDark) ResolvedTheme.Dark else ResolvedTheme.Light
    }

    // FollowApp defers to the app's themeMode; the rest override it. delegates to
    // resolve() so the System/dark-flag rule stays defined in one place.
    fun resolveWidget(widgetMode: WidgetThemeMode, appMode: ThemeMode, systemInDark: Boolean): ResolvedTheme =
        when (widgetMode) {
            WidgetThemeMode.FollowApp -> resolve(appMode, systemInDark)
            WidgetThemeMode.System -> resolve(ThemeMode.System, systemInDark)
            WidgetThemeMode.Light -> resolve(ThemeMode.Light, systemInDark)
            WidgetThemeMode.Dark -> resolve(ThemeMode.Dark, systemInDark)
            WidgetThemeMode.Amoled -> resolve(ThemeMode.Amoled, systemInDark)
        }

    fun backgroundAlpha(translucent: Boolean): Float = if (translucent) TRANSLUCENT_BACKGROUND_ALPHA else 1f

    // built on demand (not object-level vals) so a JVM unit test touching only
    // resolve() never constructs a ColorProvider. translucent lowers the surface
    // alpha only; text colors stay opaque.
    fun colors(theme: ResolvedTheme, translucent: Boolean): AgendaWidgetColors {
        val alpha = backgroundAlpha(translucent)
        return when (theme) {
            ResolvedTheme.Light -> AgendaWidgetColors(
                background = ColorProvider(Color(LIGHT_BACKGROUND).copy(alpha = alpha)),
                onBackground = ColorProvider(Color(LIGHT_ON)),
                secondary = ColorProvider(Color(LIGHT_SECONDARY)),
            )
            ResolvedTheme.Dark -> AgendaWidgetColors(
                background = ColorProvider(Color(DARK_BACKGROUND).copy(alpha = alpha)),
                onBackground = ColorProvider(Color(DARK_ON)),
                secondary = ColorProvider(Color(DARK_SECONDARY)),
            )
            ResolvedTheme.Amoled -> AgendaWidgetColors(
                background = ColorProvider(Color(AMOLED_BACKGROUND).copy(alpha = alpha)),
                onBackground = ColorProvider(Color(DARK_ON)),
                secondary = ColorProvider(Color(DARK_SECONDARY)),
            )
        }
    }
}
