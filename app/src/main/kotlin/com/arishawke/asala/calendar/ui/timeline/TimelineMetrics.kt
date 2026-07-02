/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.data.TimeUnits
import com.arishawke.asala.calendar.ui.theme.LocalIs24Hour
import com.arishawke.asala.calendar.ui.theme.Spacing
import com.arishawke.asala.calendar.ui.theme.rememberWidestTextWidth
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal val HourHeight: Dp = 72.dp
internal val DayHeight: Dp = HourHeight * 24
internal val MinEventHeight: Dp = 18.dp

// the old fixed axis width; floors the measured width so 100% scale can only
// match or widen it, never shrink the default look.
private val HourAxisWidthFloor: Dp = 48.dp

// widest formatted hour label at the current scale, plus a small margin so the
// label never crowds the first day column. shared by HourAxis and the callers
// that reserve a matching-width gutter (WeekScreen, AllDayRow).
@Composable
internal fun rememberHourAxisWidth(): Dp {
    val is24Hour = LocalIs24Hour.current
    val locale = LocalLocale.current.platformLocale
    val hourFmt = remember(is24Hour, locale) {
        if (is24Hour) DateTimeFormatter.ofPattern("HH:mm", locale) else DateTimeFormatter.ofPattern("h a", locale)
    }
    val candidates = remember(hourFmt, is24Hour) {
        (1 until TimeUnits.HoursPerDay).map { hour ->
            val label = LocalTime.of(hour, 0).format(hourFmt)
            if (is24Hour) label else label.lowercase()
        }
    }
    val measured = rememberWidestTextWidth(candidates, MaterialTheme.typography.labelSmall) + Spacing.sm
    return maxOf(HourAxisWidthFloor, measured)
}
