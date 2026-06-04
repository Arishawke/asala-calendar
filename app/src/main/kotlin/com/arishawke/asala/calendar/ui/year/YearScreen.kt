/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.year

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arishawke.asala.calendar.AsalaCalendarApplication
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.PendingDateJump
import com.kizitonwose.calendar.compose.VerticalYearCalendar
import com.kizitonwose.calendar.compose.yearcalendar.rememberYearCalendarState
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

private const val YearsEitherSide = 5
private const val YearMonthColumns = 3
private const val YearWindowRadius = 1

@Suppress("LongParameterList", "LongMethod")
@Composable
fun YearScreen(
    hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    onTitleChange: (String) -> Unit,
    todayJumpCounter: StateFlow<Int>,
    pendingDateJump: StateFlow<PendingDateJump?>,
    onConsumePendingDateJump: () -> Unit,
    modifier: Modifier = Modifier,
    firstDayOfWeekOverride: DayOfWeek? = null,
    onDayClick: (LocalDate) -> Unit = {},
    onMonthClick: (YearMonth) -> Unit = {},
    onViewedDateChange: (LocalDate) -> Unit = {},
) {
    val context = LocalContext.current
    val todayFlow = (context.applicationContext as AsalaCalendarApplication).todayProvider.today
    val vm: YearViewModel = viewModel(
        factory = YearViewModel.Factory(
            context.contentResolver,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
            todayFlow,
            YearWindowRadius,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales.get(0)
    val firstDayOfWeek = firstDayOfWeekOverride ?: firstDayOfWeekFromLocale()

    // anchor the scroll range once so a New-Year rollover doesn't reset the
    // scroll position. today's highlight still updates via state.today.
    val anchor = remember { Year.from(state.today) }

    val calendarState = rememberYearCalendarState(
        startYear = anchor.minusYears(YearsEitherSide.toLong()),
        endYear = anchor.plusYears(YearsEitherSide.toLong()),
        firstVisibleYear = anchor,
        firstDayOfWeek = firstDayOfWeek,
    )

    // title + visible-year + viewed-date follow the first visible year
    LaunchedEffect(calendarState) {
        snapshotFlow { calendarState.firstVisibleYear.year.value }
            .distinctUntilChanged()
            .collect { yr ->
                onTitleChange(yr.toString())
                vm.showYear(Year.of(yr))
                val viewed = if (state.today.year == yr) state.today else LocalDate.of(yr, 1, 1)
                onViewedDateChange(viewed)
            }
    }

    // today-jump: scroll back to the current year
    val jumpCounter by todayJumpCounter.collectAsStateWithLifecycle()
    var lastHandledJump by remember { mutableIntStateOf(jumpCounter) }
    LaunchedEffect(jumpCounter) {
        if (jumpCounter != lastHandledJump) {
            lastHandledJump = jumpCounter
            calendarState.animateScrollToYear(Year.from(state.today))
        }
    }

    // cross-view jump that explicitly targets the Year view
    val pendingJump by pendingDateJump.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJump) {
        val jump = pendingJump?.takeIf { it.view == CalendarView.Year } ?: return@LaunchedEffect
        calendarState.scrollToMonth(YearMonth.from(jump.date))
        onConsumePendingDateJump()
    }

    VerticalYearCalendar(
        state = calendarState,
        modifier = modifier.fillMaxSize(),
        monthColumns = YearMonthColumns,
        monthHorizontalSpacing = 12.dp,
        monthVerticalSpacing = 16.dp,
        dayContent = { day ->
            YearDayCell(
                day = day,
                today = state.today,
                events = state.eventsByDate[day.date].orEmpty(),
                locale = locale,
                onClick = { onDayClick(day.date) },
            )
        },
        monthHeader = { month ->
            YearMonthHeader(
                yearMonth = month.yearMonth,
                onClick = { onMonthClick(month.yearMonth) },
                locale = locale,
            )
        },
        yearHeader = { year ->
            YearHeaderLabel(year = year.year.value)
        },
    )
}
