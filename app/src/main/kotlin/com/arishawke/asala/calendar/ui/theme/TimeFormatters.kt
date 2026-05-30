/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import java.time.format.DateTimeFormatter
import java.util.Locale

// Time-of-day formatter that honors the user's 24-hour preference. Use
// rememberTimeFormatter() from any @Composable; use timeFormatter() from
// plain helpers that already have the Boolean and Locale in hand. The
// Locale parameter is load-bearing on 12-hour patterns: the AM/PM marker
// otherwise falls back to the JVM default and renders English text in
// non-English locales.

internal fun timeFormatter(is24Hour: Boolean, locale: Locale): DateTimeFormatter = if (is24Hour) {
    DateTimeFormatter.ofPattern("HH:mm", locale)
} else {
    DateTimeFormatter.ofPattern("h:mm a", locale)
}

@Composable
internal fun rememberTimeFormatter(): DateTimeFormatter {
    val is24Hour = LocalIs24Hour.current
    val locale = LocalLocale.current.platformLocale
    return remember(is24Hour, locale) { timeFormatter(is24Hour, locale) }
}
