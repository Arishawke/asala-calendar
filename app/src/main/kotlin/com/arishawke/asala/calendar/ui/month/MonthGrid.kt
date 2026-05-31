/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.multidaybars.LaneAssigner
import com.arishawke.asala.calendar.ui.multidaybars.WeekBucketer
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// Shared constants used by both paged and continuous Month surfaces.

internal const val MaxBarLanesPerWeek = 3

// EventChips' BoxWithConstraints math collapses chip capacity to zero
// below this height; pin a floor so a tiny screen does not silently
// hide every chip behind a "+N more" affordance. Continuous mode passes
// this directly; paged mode uses (parent height / 6).coerceAtLeast(this).
internal val WeekRowHeightMin: Dp = 96.dp

@Suppress("LongParameterList")
@Composable
internal fun MonthGrid(
    yearMonth: YearMonth,
    firstDayOfWeek: DayOfWeek,
    eventsByDate: Map<LocalDate, List<EventItem>>,
    allEvents: List<EventItem>,
    today: LocalDate,
    dimPastDates: Boolean,
    showWeekNumber: Boolean,
    weekRowHeight: Dp,
    onDayCellClick: (LocalDate) -> Unit,
    onOverflowClick: (LocalDate) -> Unit,
    selfContained: Boolean = false,
) {
    val days = remember(yearMonth, firstDayOfWeek) {
        buildMonthGrid(yearMonth, firstDayOfWeek)
    }
    val zone = remember { ZoneId.systemDefault() }
    // Paged mode fills a fixed 6 rows of seven equal-width cells. Self-
    // contained (continuous) mode renders only the weeks holding this
    // month's days, blanks the adjacent-month cells, and clips bars to the
    // month edge. Caller decides the row height: paged divides parent
    // height by 6; continuous passes a constant since the LazyColumn parent
    // is unbounded.
    val weekCount = if (selfContained) remember(days) { weeksWithMonthDays(days) } else 6
    Column(modifier = Modifier.fillMaxWidth()) {
        for (week in 0 until weekCount) {
            val weekDays = remember(days, week) { days.subList(week * 7, week * 7 + 7) }
            val weekStart = weekDays.first().date
            val segments = remember(weekStart, allEvents, selfContained) {
                val raw = WeekBucketer.bucketize(allEvents, weekStart, zone)
                val clipped = if (selfContained) {
                    clipSegmentsToColumns(
                        raw,
                        weekDays.indexOfFirst { it.position == DayPosition.MonthDate },
                        weekDays.indexOfLast { it.position == DayPosition.MonthDate },
                    )
                } else {
                    raw
                }
                LaneAssigner.assignLanes(clipped)
            }
            WeekLayoutRow(
                weekDays = weekDays,
                weekStart = weekStart,
                segments = segments,
                eventsByDate = eventsByDate,
                today = today,
                dimPastDates = dimPastDates,
                showWeekNumber = showWeekNumber,
                blankOutOfMonth = selfContained,
                onDayCellClick = onDayCellClick,
                onOverflowClick = onOverflowClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(weekRowHeight),
            )
        }
    }
}

internal fun buildMonthGrid(yearMonth: YearMonth, firstDayOfWeek: DayOfWeek): List<CalendarDay> {
    val firstOfMonth = yearMonth.atDay(1)
    val offset = ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
    val gridStart = firstOfMonth.minusDays(offset.toLong())
    return List(42) { i ->
        val date = gridStart.plusDays(i.toLong())
        val position = when {
            date.year == yearMonth.year && date.month == yearMonth.month ->
                DayPosition.MonthDate
            date.isBefore(firstOfMonth) -> DayPosition.InDate
            else -> DayPosition.OutDate
        }
        CalendarDay(date, position)
    }
}
