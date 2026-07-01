/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.year

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.data.EventRepository
import com.arishawke.asala.calendar.data.filteredAndRecolored
import com.arishawke.asala.calendar.ui.stateInWithToday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId

data class YearUiState(val today: LocalDate, val eventsByDate: Map<LocalDate, List<EventItem>>)

@Suppress("LongParameterList")
class YearViewModel(
    private val eventRepo: EventRepository,
    private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val todayFlow: StateFlow<LocalDate>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val initialToday: LocalDate = todayFlow.value
    private val visibleYear = MutableStateFlow(Year.from(initialToday))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventsForWindow =
        visibleYear.flatMapLatest { year ->
            val (startDate, endExclusive) = yearFetchWindow(year, YearWindowRadius)
            eventRepo.observeEvents(startDate = startDate, endExclusive = endExclusive, zone = zone)
        }

    val uiState: StateFlow<YearUiState> =
        combine(
            eventsForWindow,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
        ) { events, hidden, calOverrides, evtOverrides ->
            val visible = events.filteredAndRecolored(hidden, calOverrides, evtOverrides)
            YearUiState(
                today = todayFlow.value,
                eventsByDate = visible.groupBy { it.startDate(zone) },
            )
        }.stateInWithToday(
            scope = viewModelScope,
            todayFlow = todayFlow,
            initial = YearUiState(today = initialToday, eventsByDate = emptyMap()),
            currentToday = { it.today },
            withToday = { state, today -> state.copy(today = today) },
        )

    fun showYear(year: Year) {
        visibleYear.update { year }
    }

    companion object {
        // visible year +/- this radius is prefetched so scrolling within a year
        // doesn't reload dots. the sole caller; the pure helper keeps its param.
        private const val YearWindowRadius = 1

        // half-open [start, endExclusive).
        fun yearFetchWindow(center: Year, radiusYears: Int): Pair<LocalDate, LocalDate> {
            val start = center.minusYears(radiusYears.toLong()).atDay(1)
            val endExclusive = center.plusYears(radiusYears.toLong() + 1).atDay(1)
            return start to endExclusive
        }
    }
}
