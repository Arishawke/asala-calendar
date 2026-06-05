/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.settings

// FollowApp reuses the app's ThemeMode so a widget stays coupled to the app
// theme (the default); the rest override it independently of the app.
enum class WidgetThemeMode {
    FollowApp,
    System,
    Light,
    Dark,
    Amoled,
}
