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
import java.time.LocalDate

// Fixed leading column for the ISO 8601 week number when the Settings
// toggle is on. Narrow enough not to push day cells noticeably; wide
// enough for two digits at labelSmall plus a few px of margin.
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
            onDayCellClick = onDayCellClick,
            onOverflowClick = onOverflowClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun WeekNumberColumn(weekStart: LocalDate) {
    // ISO 8601: weeks start on Monday and week 1 is the week containing
    // the year's first Thursday, so the column's number applies to the
    // week-based year rather than the calendar year. Render against the
    // week's first ISO-week day (the Monday in that week) so partial
    // weeks at month boundaries pick up the correct number.
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

@Suppress("LongParameterList")
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
) {
    // Events that are already drawn in the week-spanning bar row above;
    // exclude them from each cell's chip stack so they don't double-render.
    val barEventIds = remember(segments) { segments.mapTo(mutableSetOf()) { it.eventId } }
    BoxWithConstraints(modifier = modifier) {
        val rowWidth = maxWidth
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (gd in weekDays) {
                    DayNumberBadge(
                        day = gd,
                        isToday = gd.date == today,
                        isPast = dimPastDates && gd.date.isBefore(today),
                        onClick = { onDayCellClick(gd.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            MultiDayBarRow(
                segments = segments,
                rowWidth = rowWidth,
                maxLanes = MaxBarLanesPerWeek,
                onSegmentClick = { eventId ->
                    // A bar tap jumps to Day view focused on the segment's
                    // first visible cell rather than opening the detail
                    // sheet; matches the chip-tap UX in this view.
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
                    // Single-day all-day events now render inline in their
                    // cell (the WeekBucketer skips them). Multi-day all-day
                    // events stay in the bar row above; filter their IDs
                    // out of the per-cell list so we don't double-render.
                    // Timed midnight-crossers continue to render only on
                    // their start day, matching prior Month-grid density.
                    val cellEvents = eventsByDate[gd.date]
                        .orEmpty()
                        .filter { it.eventId !in barEventIds }
                        .sortedWith(compareByDescending<EventItem> { it.allDay }.thenBy { it.startMillis })
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
