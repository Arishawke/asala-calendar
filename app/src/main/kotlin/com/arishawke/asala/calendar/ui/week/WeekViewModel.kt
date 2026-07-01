/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.week

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import java.time.ZoneId

data class WeekUiState(val today: LocalDate, val weekStart: LocalDate, val events: List<EventItem>)

class WeekViewModel(
    private val eventRepo: EventRepository,
    private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val todayFlow: StateFlow<LocalDate>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val initialToday: LocalDate = todayFlow.value
    private val visibleWeekStart = MutableStateFlow(initialToday)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventsForWeek =
        visibleWeekStart.flatMapLatest { ws ->
            eventRepo.observeEvents(
                startDate = ws.minusDays(7),
                endExclusive = ws.plusDays(14),
                zone = zone,
            )
        }

    val uiState: StateFlow<WeekUiState> =
        combine(
            visibleWeekStart,
            eventsForWeek,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
        ) { ws, evs, hidden, calOverrides, evtOverrides ->
            WeekUiState(
                today = todayFlow.value,
                weekStart = ws,
                events = evs.filteredAndRecolored(hidden, calOverrides, evtOverrides),
            )
        }.stateInWithToday(
            scope = viewModelScope,
            todayFlow = todayFlow,
            initial = WeekUiState(
                today = initialToday,
                weekStart = initialToday,
                events = emptyList(),
            ),
            currentToday = { it.today },
            withToday = { state, today -> state.copy(today = today) },
        )

    fun showWeek(weekStart: LocalDate) {
        visibleWeekStart.update { weekStart }
    }

    class Factory(
        private val contentResolver: ContentResolver,
        private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
        private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
        private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
        private val todayFlow: StateFlow<LocalDate>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == WeekViewModel::class.java)
            return WeekViewModel(
                eventRepo = EventRepository(contentResolver),
                hiddenCalendarIdsFlow = hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = calendarColorOverridesFlow,
                eventColorOverridesFlow = eventColorOverridesFlow,
                todayFlow = todayFlow,
            ) as T
        }
    }
}
