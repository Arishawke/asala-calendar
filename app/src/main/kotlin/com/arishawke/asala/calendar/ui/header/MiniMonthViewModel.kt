/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.header

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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class MiniMonthUiState(
    val displayedMonth: YearMonth,
    val today: LocalDate,
    val eventsByDate: Map<LocalDate, List<EventItem>>,
)

// events for the header mini-month panel. scoped to the header surface so
// `<`/`>` just retarget displayedMonth and one VM serves every view.
class MiniMonthViewModel(
    private val eventRepo: EventRepository,
    private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val todayFlow: StateFlow<LocalDate>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val displayedMonthBacker = MutableStateFlow(YearMonth.from(todayFlow.value))
    val displayedMonth: StateFlow<YearMonth> = displayedMonthBacker.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventsForMonth =
        displayedMonthBacker.flatMapLatest { ym ->
            eventRepo.observeEvents(
                startDate = ym.atDay(1),
                endExclusive = ym.atEndOfMonth().plusDays(1),
                zone = zone,
            )
        }

    val uiState: StateFlow<MiniMonthUiState> =
        combine(
            displayedMonthBacker,
            eventsForMonth,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
        ) { ym, events, hidden, calOverrides, evtOverrides ->
            val visible = events.filteredAndRecolored(hidden, calOverrides, evtOverrides)
            MiniMonthUiState(
                displayedMonth = ym,
                today = todayFlow.value,
                eventsByDate = visible.groupBy { it.startDate(zone) },
            )
        }.combine(todayFlow) { state, today ->
            if (state.today == today) state else state.copy(today = today)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MiniMonthUiState(
                displayedMonth = YearMonth.from(todayFlow.value),
                today = todayFlow.value,
                eventsByDate = emptyMap(),
            ),
        )

    fun showMonth(yearMonth: YearMonth) {
        displayedMonthBacker.update { yearMonth }
    }

    fun resetToToday() {
        displayedMonthBacker.update { YearMonth.from(todayFlow.value) }
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
            require(modelClass == MiniMonthViewModel::class.java)
            return MiniMonthViewModel(
                eventRepo = EventRepository(contentResolver),
                hiddenCalendarIdsFlow = hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = calendarColorOverridesFlow,
                eventColorOverridesFlow = eventColorOverridesFlow,
                todayFlow = todayFlow,
            ) as T
        }
    }
}
