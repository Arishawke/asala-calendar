/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.schedule

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.data.EventRepository
import com.arishawke.asala.calendar.data.filteredAndRecolored
import com.arishawke.asala.calendar.ui.timeline.clipToDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val first = Instant.ofEpochMilli(e.startMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val last = Instant.ofEpochMilli(e.endMillis).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
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
    val first = Instant.ofEpochMilli(row.event.startMillis).atZone(ZoneOffset.UTC).toLocalDate()
    first.plusDays((row.dayIndex - 1).toLong())
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
    private val windowStart = initialToday.minusDays(WindowBeforeDays)
    private val windowEndExclusive = initialToday.plusDays(WindowAfterDays)

    private val events = eventRepo.observeEvents(
        startDate = windowStart,
        endExclusive = windowEndExclusive,
        zone = zone,
    )

    val uiState: StateFlow<ScheduleUiState> =
        combine(
            events,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
            todayFlow,
        ) { all, hidden, calOverrides, evtOverrides, today ->
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
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ScheduleUiState(
                today = initialToday,
                daysInOrder = emptyList(),
                rowsByDate = emptyMap(),
            ),
        )

    class Factory(
        private val contentResolver: ContentResolver,
        private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
        private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
        private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
        private val todayFlow: StateFlow<LocalDate>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == ScheduleViewModel::class.java)
            return ScheduleViewModel(
                eventRepo = EventRepository(contentResolver),
                hiddenCalendarIdsFlow = hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = calendarColorOverridesFlow,
                eventColorOverridesFlow = eventColorOverridesFlow,
                todayFlow = todayFlow,
            ) as T
        }
    }

    private companion object {
        const val WindowBeforeDays = 7L
        const val WindowAfterDays = 60L
    }
}
