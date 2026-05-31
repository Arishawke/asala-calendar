/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.threeday

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arishawke.asala.calendar.AsalaCalendarApplication
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.PendingDateJump
import com.arishawke.asala.calendar.ui.theme.rememberCalendarPagerFling
import com.arishawke.asala.calendar.ui.week.WeekPage
import com.arishawke.asala.calendar.ui.week.formatWeekRange
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneId

@Composable
// LongMethod: same shape as DayScreen (pager state + today jump + cross-view
// jump + render), pushed over detekt's 60-line default.
@Suppress("LongParameterList", "LongMethod")
fun ThreeDayScreen(
    hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    onTitleChange: (String) -> Unit,
    todayJumpCounter: StateFlow<Int>,
    pendingDateJump: StateFlow<PendingDateJump?>,
    onConsumePendingDateJump: () -> Unit,
    modifier: Modifier = Modifier,
    workingHoursEnabled: Boolean = false,
    workingHoursStartHour: Int = 9,
    workingHoursEndHour: Int = 17,
    workingDaysEnabled: Boolean = false,
    workingDaysMask: Long = 0L,
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit = { _, _ -> },
    onReschedule: (eventId: Long, instanceMillis: Long, newStartMillis: Long) -> Unit = { _, _, _ -> },
    onViewedDateChange: (LocalDate) -> Unit = {},
) {
    val context = LocalContext.current
    val todayFlow = (context.applicationContext as AsalaCalendarApplication).todayProvider.today
    val vm: ThreeDayViewModel = viewModel(
        factory = ThreeDayViewModel.Factory(
            context.contentResolver,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
            todayFlow,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    val center = ThreeDayViewModel.WindowPagesEachSide
    val pageCount = center * 2 + 1
    val pagerState = rememberPagerState(initialPage = center) { pageCount }

    val zone = remember { ZoneId.systemDefault() }
    val locale = LocalConfiguration.current.locales.get(0)
    // rolling anchor: today sits in column 0 of the center page.
    val anchor = state.today

    LaunchedEffect(pagerState, anchor) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val start = pageStart(anchor, page, center)
            val end = start.plusDays((ThreeDayPageSize - 1).toLong())
            vm.selectDate(start)
            onTitleChange(formatWeekRange(start, end, locale))
            // FAB default: today if visible, else page start so a future add lands inside.
            val viewedDate = if (state.today in start..end) state.today else start
            onViewedDateChange(viewedDate)
        }
    }

    // Jump-to-today.
    val jumpCounter by todayJumpCounter.collectAsStateWithLifecycle()
    var lastHandledJump by remember { mutableIntStateOf(jumpCounter) }
    LaunchedEffect(jumpCounter) {
        if (jumpCounter != lastHandledJump) {
            lastHandledJump = jumpCounter
            pagerState.animateScrollToPage(center)
        }
    }

    // cross-view jump from the mini-month. filtered by target view so a jump
    // meant for another view doesn't scroll here.
    val pendingJump by pendingDateJump.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJump, anchor) {
        val jump = pendingJump?.takeIf { it.view == CalendarView.ThreeDay } ?: return@LaunchedEffect
        val target = pageForDate(anchor, jump.date, center).coerceIn(0, pageCount - 1)
        pagerState.animateScrollToPage(target)
        onConsumePendingDateJump()
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        flingBehavior = rememberCalendarPagerFling(pagerState),
    ) { page ->
        val days = remember(page, anchor, center) {
            val start = pageStart(anchor, page, center)
            (0 until ThreeDayPageSize).map { start.plusDays(it.toLong()) }
        }
        WeekPage(
            days = days,
            today = state.today,
            events = state.events,
            zone = zone,
            dimPastDates = false,
            workingHoursEnabled = workingHoursEnabled,
            workingHoursStartHour = workingHoursStartHour,
            workingHoursEndHour = workingHoursEndHour,
            workingDaysEnabled = workingDaysEnabled,
            workingDaysMask = workingDaysMask,
            showWeekNumber = false,
            enableOverflow = false,
            onEventClick = onEventClick,
            onReschedule = onReschedule,
        )
    }
}
