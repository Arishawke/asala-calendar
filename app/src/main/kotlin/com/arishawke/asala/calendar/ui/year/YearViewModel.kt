/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.year

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.data.EventRepository
import com.arishawke.asala.calendar.data.filteredAndRecolored
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId

// Keep the upstream collector warm briefly after the screen leaves so a
// quick return does not re-query the provider. Matches MonthViewModel.
private const val StopTimeoutMillis = 5_000L

data class YearUiState(val today: LocalDate, val eventsByDate: Map<LocalDate, List<EventItem>>)

@Suppress("LongParameterList")
class YearViewModel(
    private val eventRepo: EventRepository,
    private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val todayFlow: StateFlow<LocalDate>,
    private val yearWindowRadius: Int = 1,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val initialToday: LocalDate = todayFlow.value
    private val visibleYear = MutableStateFlow(Year.from(initialToday))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventsForWindow =
        visibleYear.flatMapLatest { year ->
            val (startDate, endExclusive) = yearFetchWindow(year, yearWindowRadius)
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
        }.combine(todayFlow) { state, today ->
            if (state.today == today) state else state.copy(today = today)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(StopTimeoutMillis),
            initialValue = YearUiState(today = initialToday, eventsByDate = emptyMap()),
        )

    fun showYear(year: Year) {
        visibleYear.update { year }
    }

    class Factory(
        private val contentResolver: ContentResolver,
        private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
        private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
        private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
        private val todayFlow: StateFlow<LocalDate>,
        private val yearWindowRadius: Int = 1,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == YearViewModel::class.java)
            return YearViewModel(
                eventRepo = EventRepository(contentResolver),
                hiddenCalendarIdsFlow = hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = calendarColorOverridesFlow,
                eventColorOverridesFlow = eventColorOverridesFlow,
                todayFlow = todayFlow,
                yearWindowRadius = yearWindowRadius,
            ) as T
        }
    }

    companion object {
        // Load events for the visible year +/- radius years so the year
        // grid's mini-month dots render without reloading on every scroll
        // within a year. Half-open [start, endExclusive).
        fun yearFetchWindow(center: Year, radiusYears: Int): Pair<LocalDate, LocalDate> {
            val start = center.minusYears(radiusYears.toLong()).atDay(1)
            val endExclusive = center.plusYears(radiusYears.toLong() + 1).atDay(1)
            return start to endExclusive
        }
    }
}
