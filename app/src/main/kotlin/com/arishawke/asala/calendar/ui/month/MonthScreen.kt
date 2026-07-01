/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arishawke.asala.calendar.AsalaCalendarApplication
import com.arishawke.asala.calendar.PendingDateJump
import com.arishawke.asala.calendar.ui.EventViewModelFactory
import com.arishawke.asala.calendar.ui.settings.MonthScrollStyle
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// continuous half-window radius; must stay > paged's 1 so a fling doesn't render blank pre-composed cells
internal const val ContinuousMonthWindowRadius = 6

// dispatcher: hoists the VM so both subscreens share one instance; re-keyed on style flip for the new radius
@Suppress("LongParameterList")
@Composable
fun MonthScreen(
    hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    onTitleChange: (String) -> Unit,
    todayJumpCounter: StateFlow<Int>,
    pendingDateJump: StateFlow<PendingDateJump?>,
    onConsumePendingDateJump: () -> Unit,
    onViewedMonthChange: (YearMonth) -> Unit,
    monthScrollStyle: MonthScrollStyle,
    modifier: Modifier = Modifier,
    firstDayOfWeekOverride: DayOfWeek? = null,
    dimPastDates: Boolean = false,
    showWeekNumber: Boolean = false,
    onDayCellClick: (LocalDate) -> Unit = {},
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit = { _, _ -> },
    onViewedDateChange: (LocalDate) -> Unit = {},
) {
    val context = LocalContext.current
    val todayFlow = (context.applicationContext as AsalaCalendarApplication).todayProvider.today
    val radius = when (monthScrollStyle) {
        MonthScrollStyle.Paged -> 1
        MonthScrollStyle.Continuous -> ContinuousMonthWindowRadius
    }
    val vm: MonthViewModel = viewModel(
        key = "month-scroll-${monthScrollStyle.name}",
        factory = EventViewModelFactory(context.contentResolver, MonthViewModel::class.java) { repo ->
            MonthViewModel(
                eventRepo = repo,
                hiddenCalendarIdsFlow = hiddenCalendarIdsFlow,
                calendarColorOverridesFlow = calendarColorOverridesFlow,
                eventColorOverridesFlow = eventColorOverridesFlow,
                todayFlow = todayFlow,
                monthWindowRadius = radius,
            )
        },
    )

    when (monthScrollStyle) {
        MonthScrollStyle.Paged -> PagedMonthScreen(
            vm = vm,
            onTitleChange = onTitleChange,
            todayJumpCounter = todayJumpCounter,
            pendingDateJump = pendingDateJump,
            onConsumePendingDateJump = onConsumePendingDateJump,
            onViewedMonthChange = onViewedMonthChange,
            modifier = modifier,
            firstDayOfWeekOverride = firstDayOfWeekOverride,
            dimPastDates = dimPastDates,
            showWeekNumber = showWeekNumber,
            onDayCellClick = onDayCellClick,
            onEventClick = onEventClick,
            onViewedDateChange = onViewedDateChange,
        )
        MonthScrollStyle.Continuous -> ContinuousMonthScreen(
            vm = vm,
            onTitleChange = onTitleChange,
            todayJumpCounter = todayJumpCounter,
            pendingDateJump = pendingDateJump,
            onConsumePendingDateJump = onConsumePendingDateJump,
            onViewedMonthChange = onViewedMonthChange,
            modifier = modifier,
            firstDayOfWeekOverride = firstDayOfWeekOverride,
            dimPastDates = dimPastDates,
            showWeekNumber = showWeekNumber,
            onDayCellClick = onDayCellClick,
            onEventClick = onEventClick,
            onViewedDateChange = onViewedDateChange,
        )
    }
}
