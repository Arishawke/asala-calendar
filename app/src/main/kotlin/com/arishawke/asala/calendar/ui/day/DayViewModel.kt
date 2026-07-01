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
import com.arishawke.asala.calendar.ui.stateInWithToday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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

    private val initialToday: LocalDate = todayFlow.value
    private val selectedDate = MutableStateFlow(initialToday)

    // load events for a small window around the day in view, re-querying as the
    // user navigates (like the week view). this lets a jump from the month view
    // reach any day with its events, instead of a fixed window pinned to today.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val events = selectedDate.flatMapLatest { d ->
        eventRepo.observeEvents(
            startDate = d.minusDays(DayLoadBufferDays),
            endExclusive = d.plusDays(DayLoadBufferDays + 1),
            zone = zone,
        )
    }

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
        }.stateInWithToday(
            scope = viewModelScope,
            todayFlow = todayFlow,
            initial = DayUiState(
                today = initialToday,
                selectedDate = initialToday,
                events = emptyList(),
            ),
            currentToday = { it.today },
            withToday = { state, today -> state.copy(today = today) },
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
        // days loaded either side of the day in view, so adjacent pages the pager
        // pre-renders already have their events before selectedDate catches up.
        private const val DayLoadBufferDays: Long = 7L
    }
}
