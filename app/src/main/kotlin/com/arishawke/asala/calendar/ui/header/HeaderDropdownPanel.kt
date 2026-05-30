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

// Dispatches between Month-chips and mini-month panels based on the
// active view. Month gets a quick-jump chip strip; the timeline-style
// views get the mini-month with event dots so picking a single date is
// the natural target.
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
        CalendarView.Tasks -> Unit
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
            // Reset to today's month whenever the user opens the panel
            // from a fresh state. Driven by the panel's recomposition
            // entering through the AnimatedVisibility (key change triggers
            // the effect).
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
