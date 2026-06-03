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

// one event reduced to what a day chip needs. allDay sorts before timed so chip
// order matches the agenda's within-day order.
data class MonthEvent(
    val date: LocalDate,
    val startMillis: Long,
    val allDay: Boolean,
    val colorArgb: Int,
    val title: String,
)

// a single chip entry: color bar + title text.
data class MonthCellEvent(val title: String, val colorArgb: Int)

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
