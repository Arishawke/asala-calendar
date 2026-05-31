/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

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
import java.time.YearMonth
import java.time.ZoneId

data class MonthUiState(
    val yearMonth: YearMonth,
    val today: LocalDate,
    val eventsByDate: Map<LocalDate, List<EventItem>>,
    val events: List<EventItem>,
)

@Suppress("LongParameterList")
class MonthViewModel(
    private val eventRepo: EventRepository,
    private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    private val todayFlow: StateFlow<LocalDate>,
    private val monthWindowRadius: Int = 1,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val initialToday: LocalDate = todayFlow.value
    private val visibleMonth = MutableStateFlow(YearMonth.from(initialToday))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventsForMonth =
        visibleMonth.flatMapLatest { ym ->
            val (startDate, endExclusive) = monthFetchWindow(ym, monthWindowRadius)
            eventRepo.observeEvents(
                startDate = startDate,
                endExclusive = endExclusive,
                zone = zone,
            )
        }

    val uiState: StateFlow<MonthUiState> =
        combine(
            visibleMonth,
            eventsForMonth,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
        ) { ym, events, hidden, calOverrides, evtOverrides ->
            val visible = events.filteredAndRecolored(hidden, calOverrides, evtOverrides)
            val byDate = visible.groupBy { it.startDate(zone) }
            MonthUiState(
                yearMonth = ym,
                today = todayFlow.value,
                eventsByDate = byDate,
                events = visible,
            )
        }.combine(todayFlow) { state, today ->
            if (state.today == today) state else state.copy(today = today)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
            MonthUiState(
                yearMonth = YearMonth.from(initialToday),
                today = initialToday,
                eventsByDate = emptyMap(),
                events = emptyList(),
            ),
        )

    fun showMonth(yearMonth: YearMonth) {
        visibleMonth.update { yearMonth }
    }

    class Factory(
        private val contentResolver: ContentResolver,
        private val hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
        private val calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
        private val eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
        private val todayFlow: StateFlow<LocalDate>,
        private val monthWindowRadius: Int = 1,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == MonthViewModel::class.java)
            return MonthViewModel(
                eventRepo = EventRepository(contentResolver),
                hiddenCalendarIdsFlow = hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = calendarColorOverridesFlow,
                eventColorOverridesFlow = eventColorOverridesFlow,
                todayFlow = todayFlow,
                monthWindowRadius = monthWindowRadius,
            ) as T
        }
    }

    companion object {
        // fetch window = center +/- radius months; paged passes 1, continuous a wider value for prefetch
        fun monthFetchWindow(center: YearMonth, radius: Int): Pair<LocalDate, LocalDate> {
            val start = center.minusMonths(radius.toLong()).atDay(1)
            val endExclusive = center.plusMonths(radius.toLong() + 1).atDay(1)
            return start to endExclusive
        }
    }
}
