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
import androidx.compose.ui.unit.dp
import androidx.glance.unit.ColorProvider
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val ACCENT_LIGHT = 0xFF3B6DF6L
private const val ACCENT_DARK = 0xFF5C8DF6L

internal fun monthLabel(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))

internal fun weekdayNarrow(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.NARROW, Locale.getDefault())

internal fun monthAccent(theme: ResolvedTheme): ColorProvider = when (theme) {
    ResolvedTheme.Light -> ColorProvider(Color(ACCENT_LIGHT))
    ResolvedTheme.Dark, ResolvedTheme.Amoled -> ColorProvider(Color(ACCENT_DARK))
}

// how many event chips fit per day cell at a given widget height (dp) and grid
// row count: one chip (plus a "+N") when the widget is short, up to MAX_CHIPS
// when it is tall. heights are approximate; tune the constants if the count is off.
private const val CHROME_HEIGHT_DP = 46f // month label + weekday row + paddings
private const val CELL_NUMBER_HEIGHT_DP = 18f // the day-number line
private const val CHIP_LINE_HEIGHT_DP = 14f // one chip plus its gap

internal fun maxChipsPerCell(heightDp: Float, rows: Int): Int {
    val cell = (heightDp - CHROME_HEIGHT_DP) / rows
    val chips = ((cell - CELL_NUMBER_HEIGHT_DP) / CHIP_LINE_HEIGHT_DP).toInt()
    return chips.coerceIn(1, MonthGridBuilder.MAX_CHIPS)
}

@Suppress("MagicNumber")
internal object MonthDimens {
    val pad = 10.dp
    val corner = 16.dp
    val headerGap = 4.dp
    val cellPad = 1.dp
    val todayCircle = 22.dp
    // chip: single-text pill with translucent event color background
    val chipCorner = 4.dp
    val chipGap = 2.dp
    val chipPadH = 3.dp
    val chipPadV = 1.dp
}
