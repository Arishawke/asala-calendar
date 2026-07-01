/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.threeday

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.data.EventRepository
import com.arishawke.asala.calendar.data.filteredAndRecolored
import com.arishawke.asala.calendar.ui.stateInWithToday
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

data class ThreeDayUiState(val today: LocalDate, val selectedDate: LocalDate, val events: List<EventItem>)

class ThreeDayViewModel(
    private val eventRepo: EventRepository,
    private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val todayFlow: StateFlow<LocalDate>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    // window captured once: shifting it at midnight would re-subscribe and churn
    // the ContentObserver. today highlight still refreshes via todayFlow.
    private val initialToday: LocalDate = todayFlow.value
    private val selectedDate = MutableStateFlow(initialToday)
    private val windowStart = initialToday.minusDays((WindowPagesEachSide * ThreeDayPageSize).toLong())

    // +ThreeDayPageSize (not +1): the furthest page spans three days, so the
    // exclusive end must clear all three.
    private val windowEndExclusive =
        initialToday.plusDays((WindowPagesEachSide * ThreeDayPageSize + ThreeDayPageSize).toLong())

    private val events = eventRepo.observeEvents(
        startDate = windowStart,
        endExclusive = windowEndExclusive,
        zone = zone,
    )

    val uiState: StateFlow<ThreeDayUiState> =
        combine(
            selectedDate,
            events,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
        ) { d, evs, hidden, calOverrides, evtOverrides ->
            ThreeDayUiState(
                today = todayFlow.value,
                selectedDate = d,
                events = evs.filteredAndRecolored(hidden, calOverrides, evtOverrides),
            )
        }.stateInWithToday(
            scope = viewModelScope,
            todayFlow = todayFlow,
            initial = ThreeDayUiState(
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
            require(modelClass == ThreeDayViewModel::class.java)
            return ThreeDayViewModel(
                eventRepo = EventRepository(contentResolver),
                hiddenCalendarIdsFlow = hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = calendarColorOverridesFlow,
                eventColorOverridesFlow = eventColorOverridesFlow,
                todayFlow = todayFlow,
            ) as T
        }
    }

    companion object {
        // 20 pages * 3 days = +/-60 days each side, matching Day's reach.
        const val WindowPagesEachSide: Int = 20
    }
}
