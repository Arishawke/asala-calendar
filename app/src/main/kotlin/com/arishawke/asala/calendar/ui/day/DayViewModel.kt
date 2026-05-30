/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.day

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.data.EventRepository
import com.arishawke.asala.calendar.data.filteredAndRecolored
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

data class DayUiState(val today: LocalDate, val selectedDate: LocalDate, val events: List<EventItem>)

class DayViewModel(
    private val eventRepo: EventRepository,
    private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val todayFlow: StateFlow<LocalDate>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    // Initial today is captured for the event-fetch window: shifting the
    // window dynamically would re-subscribe the upstream observer every
    // midnight, churning the ContentObserver. The today-highlight inside
    // UiState refreshes via todayFlow regardless, which is the consumer
    // the user actually sees.
    private val initialToday: LocalDate = todayFlow.value
    private val selectedDate = MutableStateFlow(initialToday)
    private val windowStart = initialToday.minusDays(WindowDaysEachSide)
    private val windowEndExclusive = initialToday.plusDays(WindowDaysEachSide + 1)

    private val events = eventRepo.observeEvents(
        startDate = windowStart,
        endExclusive = windowEndExclusive,
        zone = zone,
    )

    val uiState: StateFlow<DayUiState> =
        combine(
            selectedDate,
            events,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
        ) { d, evs, hidden, calOverrides, evtOverrides ->
            DayUiState(
                today = todayFlow.value,
                selectedDate = d,
                events = evs.filteredAndRecolored(hidden, calOverrides, evtOverrides),
            )
        }.combine(todayFlow) { state, today ->
            if (state.today == today) state else state.copy(today = today)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DayUiState(
                today = initialToday,
                selectedDate = initialToday,
                events = emptyList(),
            ),
        )

    fun selectDate(date: LocalDate) {
        selectedDate.update { date }
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
            require(modelClass == DayViewModel::class.java)
            return DayViewModel(
                eventRepo = EventRepository(contentResolver),
                hiddenCalendarIdsFlow = hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = calendarColorOverridesFlow,
                eventColorOverridesFlow = eventColorOverridesFlow,
                todayFlow = todayFlow,
            ) as T
        }
    }

    companion object {
        const val WindowDaysEachSide: Long = 60L
    }
}
