/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.data.EventRepository
import com.arishawke.asala.calendar.data.filteredAndRecolored
import com.arishawke.asala.calendar.ui.UiStateStopTimeoutMillis
import com.arishawke.asala.calendar.ui.timeline.clipToDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// one row per covered day for multi-day events. all-day dates use UTC
// because CalendarContract stores them that way.
internal fun expandToScheduleRows(
    events: List<EventItem>,
    zone: ZoneId,
    windowStart: LocalDate,
    windowEndExclusive: LocalDate,
): List<ScheduleRow> = events.flatMap { e ->
    if (e.allDay) {
        expandAllDay(e, windowStart, windowEndExclusive)
    } else {
        expandTimed(e, zone, windowStart, windowEndExclusive)
    }
}

private fun expandAllDay(e: EventItem, windowStart: LocalDate, windowEndExclusive: LocalDate): List<ScheduleRow> {
    // all-day dates are UTC; EventItem owns the span math (incl. the malformed-row clamp).
    val first = e.startDate(ZoneOffset.UTC)
    val last = e.lastDate(ZoneOffset.UTC)
    val total = (last.toEpochDay() - first.toEpochDay()).toInt() + 1
    return (0 until total)
        .map { i -> i + 1 to first.plusDays(i.toLong()) }
        .filter { (_, day) -> !day.isBefore(windowStart) && day.isBefore(windowEndExclusive) }
        .map { (idx, _) -> ScheduleRow(event = e, dayIndex = idx, totalDays = total) }
}

// midnight-crossers expand into one row per covered day with clipped
// display millis so each row shows only its slice.
private fun expandTimed(
    e: EventItem,
    zone: ZoneId,
    windowStart: LocalDate,
    windowEndExclusive: LocalDate,
): List<ScheduleRow> {
    val firstDay = maxOf(e.startDate(zone), windowStart)
    val lastDay = minOf(e.endDate(zone), windowEndExclusive.minusDays(1))
    if (firstDay.isAfter(lastDay)) return emptyList()
    val days = (firstDay.toEpochDay()..lastDay.toEpochDay()).map { LocalDate.ofEpochDay(it) }
    return days.mapNotNull { day ->
        val clip = clipToDay(e, day, zone) ?: return@mapNotNull null
        ScheduleRow(
            event = e,
            dayIndex = clip.segmentIndex,
            totalDays = clip.segmentCount,
            displayStartMillis = clip.displayStartMillis,
            displayEndMillis = clip.displayEndMillis,
        )
    }
}

internal fun rowDate(row: ScheduleRow, zone: ZoneId): LocalDate = if (row.event.allDay) {
    row.event.startDate(ZoneOffset.UTC).plusDays((row.dayIndex - 1).toLong())
} else {
    Instant.ofEpochMilli(row.displayStartMillis).atZone(zone).toLocalDate()
}

// line sits above the first not-yet-started timed row. start-based, not
// end-based: anchoring to end time jumped the line up to a long ongoing
// event's start (the reported bug). all-day rows are skipped. returns
// rows.size when every timed row has already started.
internal fun scheduleNowLineIndex(rows: List<ScheduleRow>, nowMillis: Long): Int =
    rows.indexOfFirst { !it.event.allDay && it.displayStartMillis >= nowMillis }
        .let { if (it < 0) rows.size else it }

// dayIndex/totalDays drive the "Day N/M" badge for multi-day events;
// displayStart/End default to real bounds, overridden per day for
// midnight-crossers.
data class ScheduleRow(
    val event: EventItem,
    val dayIndex: Int = 1,
    val totalDays: Int = 1,
    val displayStartMillis: Long = event.startMillis,
    val displayEndMillis: Long = event.endMillis,
)

data class ScheduleUiState(
    val today: LocalDate,
    val daysInOrder: List<LocalDate>,
    val rowsByDate: Map<LocalDate, List<ScheduleRow>>,
)

class ScheduleViewModel(
    private val eventRepo: EventRepository,
    private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val todayFlow: StateFlow<LocalDate>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val initialToday: LocalDate = todayFlow.value

    // window follows today so the +60d forward horizon doesn't shrink as
    // midnights pass in a long session; flatMapLatest re-queries on a new day.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventsForWindow =
        todayFlow.flatMapLatest { today ->
            eventRepo.observeEvents(
                startDate = today.minusDays(WindowBeforeDays),
                endExclusive = today.plusDays(WindowAfterDays),
                zone = zone,
            )
        }

    val uiState: StateFlow<ScheduleUiState> =
        combine(
            eventsForWindow,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
            todayFlow,
        ) { all, hidden, calOverrides, evtOverrides, today ->
            val windowStart = today.minusDays(WindowBeforeDays)
            val windowEndExclusive = today.plusDays(WindowAfterDays)
            val visible = all.filteredAndRecolored(hidden, calOverrides, evtOverrides)
            val rows = expandToScheduleRows(visible, zone, windowStart, windowEndExclusive)
            // all-day rows lead each day; timed rows follow by start time
            val byDate = rows
                .groupBy { rowDate(it, zone) }
                .mapValues { (_, dayRows) ->
                    dayRows.sortedWith(
                        compareByDescending<ScheduleRow> { it.event.allDay }
                            .thenBy { it.displayStartMillis }
                            .thenBy { it.dayIndex },
                    )
                }
            ScheduleUiState(
                today = today,
                daysInOrder = byDate.keys.sorted(),
                rowsByDate = byDate,
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(UiStateStopTimeoutMillis),
                initialValue = ScheduleUiState(
                    today = initialToday,
                    daysInOrder = emptyList(),
                    rowsByDate = emptyMap(),
                ),
            )

    private companion object {
        const val WindowBeforeDays = 7L
        const val WindowAfterDays = 60L
    }
}
