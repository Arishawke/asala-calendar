/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arishawke.asala.calendar.AsalaCalendarApplication
import com.arishawke.asala.calendar.CalendarView
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// Month gets the quick-jump chip strip; timeline views get the mini-month
// with event dots since their natural target is a single date.
@Composable
internal fun HeaderDropdownPanel(
    currentView: CalendarView,
    viewedMonth: YearMonth,
    today: LocalDate,
    firstDayOfWeekOverride: DayOfWeek?,
    hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    onSelectMonth: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (currentView) {
        CalendarView.Tasks, CalendarView.Year -> Unit
        CalendarView.Month -> MonthChipsPanel(
            viewedMonth = viewedMonth,
            today = YearMonth.from(today),
            onSelectMonth = onSelectMonth,
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        )
        CalendarView.Week, CalendarView.ThreeDay, CalendarView.Day, CalendarView.Schedule -> {
            val context = LocalContext.current
            val todayFlow = (context.applicationContext as AsalaCalendarApplication).todayProvider.today
            val vm: MiniMonthViewModel = viewModel(
                factory = MiniMonthViewModel.Factory(
                    context.contentResolver,
                    hiddenCalendarIdsFlow,
                    calendarColorOverridesFlow,
                    eventColorOverridesFlow,
                    todayFlow,
                ),
            )
            // reset to today's month each time the panel re-enters.
            LaunchedEffect(currentView) { vm.resetToToday() }

            val state by vm.uiState.collectAsStateWithLifecycle()
            MiniMonthPanel(
                displayedMonth = state.displayedMonth,
                today = state.today,
                firstDayOfWeek = firstDayOfWeekOverride ?: firstDayOfWeekFromLocale(),
                eventsByDate = state.eventsByDate,
                onSelectDate = onSelectDate,
                onShiftMonth = { delta -> vm.showMonth(state.displayedMonth.plusMonths(delta.toLong())) },
                modifier = modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}
