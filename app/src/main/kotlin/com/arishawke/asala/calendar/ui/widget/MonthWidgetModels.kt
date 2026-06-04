/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

enum class MonthWidgetState { Ready, NoPermission }

// one event reduced to what the grid needs: its grid-clamped covered range plus
// sort keys. allDay sorts before timed within a single day's chips.
data class MonthEvent(
    val firstCovered: LocalDate,
    val lastCovered: LocalDate,
    val startMillis: Long,
    val allDay: Boolean,
    val colorArgb: Int,
    val title: String,
)

// one day's slice of an event. isLabel = show the title (band start or a week's
// first column); otherwise a title-less continuation strip. multiDay events
// render as a square, edge-to-edge band so adjacent days fuse; single-day events
// stay rounded, inset pills.
data class MonthCellEvent(
    val title: String,
    val colorArgb: Int,
    val isLabel: Boolean,
    val multiDay: Boolean,
)

data class MonthDayCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val events: List<MonthCellEvent>,
    val moreCount: Int,
)

data class MonthGridData(
    val month: YearMonth,
    val weekStart: DayOfWeek,
    val weeks: List<List<MonthDayCell>>,
)

data class MonthWidgetSnapshot(val state: MonthWidgetState, val grid: MonthGridData?)
