/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.multidaybars.MultiDayBarRow
import com.arishawke.asala.calendar.ui.multidaybars.WeekSegment
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import java.time.LocalDate

// leading week-number column width: fits two digits at labelSmall without crowding the cells
internal val WeekNumberColumnWidth: Dp = 28.dp

@Suppress("LongParameterList")
@Composable
internal fun WeekLayoutRow(
    weekDays: List<CalendarDay>,
    weekStart: LocalDate,
    segments: List<WeekSegment>,
    eventsByDate: Map<LocalDate, List<EventItem>>,
    today: LocalDate,
    dimPastDates: Boolean,
    showWeekNumber: Boolean,
    onDayCellClick: (LocalDate) -> Unit,
    onOverflowClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    blankOutOfMonth: Boolean = false,
) {
    if (showWeekNumber) {
        Row(modifier = modifier) {
            WeekNumberColumn(weekStart = weekStart)
            WeekLayoutRowCore(
                weekDays = weekDays,
                weekStart = weekStart,
                segments = segments,
                eventsByDate = eventsByDate,
                today = today,
                dimPastDates = dimPastDates,
                blankOutOfMonth = blankOutOfMonth,
                onDayCellClick = onDayCellClick,
                onOverflowClick = onOverflowClick,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        WeekLayoutRowCore(
            weekDays = weekDays,
            weekStart = weekStart,
            segments = segments,
            eventsByDate = eventsByDate,
            today = today,
            dimPastDates = dimPastDates,
            blankOutOfMonth = blankOutOfMonth,
            onDayCellClick = onDayCellClick,
            onOverflowClick = onOverflowClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun WeekNumberColumn(weekStart: LocalDate) {
    // ISO week number is week-based-year, anchored on the week's Monday so boundary weeks number correctly
    val isoMonday = weekStart.with(java.time.DayOfWeek.MONDAY)
    val weekNumber = isoMonday.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
    Box(
        modifier = Modifier
            .width(WeekNumberColumnWidth)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = weekNumber.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// LongMethod: numbers, bars, and chips rows share one BoxWithConstraints
// scope; splitting would thread that scope through callees.
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun WeekLayoutRowCore(
    weekDays: List<CalendarDay>,
    weekStart: LocalDate,
    segments: List<WeekSegment>,
    eventsByDate: Map<LocalDate, List<EventItem>>,
    today: LocalDate,
    dimPastDates: Boolean,
    onDayCellClick: (LocalDate) -> Unit,
    onOverflowClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    blankOutOfMonth: Boolean = false,
) {
    // bar-row events, excluded from cell chips so they don't double-render
    val barEventIds = remember(segments) { segments.mapTo(mutableSetOf()) { it.eventId } }
    BoxWithConstraints(modifier = modifier) {
        val rowWidth = maxWidth
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (gd in weekDays) {
                    if (blankOutOfMonth && gd.position != DayPosition.MonthDate) {
                        Box(modifier = Modifier.weight(1f))
                    } else {
                        DayNumberBadge(
                            day = gd,
                            isToday = gd.date == today,
                            isPast = dimPastDates && gd.date.isBefore(today),
                            onClick = { onDayCellClick(gd.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            MultiDayBarRow(
                segments = segments,
                rowWidth = rowWidth,
                maxLanes = MaxBarLanesPerWeek,
                onSegmentClick = { eventId ->
                    // bar tap jumps to Day view on the segment's first cell, matching chip-tap UX
                    val seg = segments.firstOrNull { it.eventId == eventId }
                    val date = seg?.let { weekDays[it.startCol].date } ?: weekStart
                    onDayCellClick(date)
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                for (gd in weekDays) {
                    if (blankOutOfMonth && gd.position != DayPosition.MonthDate) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        continue
                    }
                    // single-day all-day events render inline here; multi-day ones
                    // live in the bar row, so drop their ids. keyed on the date so a
                    // reused cell slot recomputes rather than reusing another day's list.
                    val cellEvents = remember(eventsByDate, gd.date, barEventIds) {
                        eventsByDate[gd.date]
                            .orEmpty()
                            .filter { it.eventId !in barEventIds }
                            .sortedWith(compareByDescending<EventItem> { it.allDay }.thenBy { it.startMillis })
                    }
                    DayCell(
                        day = gd,
                        events = cellEvents,
                        isPast = dimPastDates && gd.date.isBefore(today),
                        onClick = { onDayCellClick(gd.date) },
                        onEventClick = { _, _ -> onDayCellClick(gd.date) },
                        onOverflowClick = { onOverflowClick(gd.date) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}
