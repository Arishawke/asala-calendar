/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.PendingDateJump
import com.arishawke.asala.calendar.ui.theme.rememberCalendarPagerFling
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val PageRangeMonths = 120
private const val InitialPage = PageRangeMonths
private const val PageCount = PageRangeMonths * 2 + 1

@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun PagedMonthScreen(
    vm: MonthViewModel,
    onTitleChange: (String) -> Unit,
    todayJumpCounter: StateFlow<Int>,
    pendingDateJump: StateFlow<PendingDateJump?>,
    onConsumePendingDateJump: () -> Unit,
    onViewedMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    firstDayOfWeekOverride: DayOfWeek? = null,
    dimPastDates: Boolean = false,
    showWeekNumber: Boolean = false,
    onDayCellClick: (LocalDate) -> Unit = {},
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit = { _, _ -> },
    onViewedDateChange: (LocalDate) -> Unit = {},
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var overflowDate by remember { mutableStateOf<LocalDate?>(null) }

    val firstDayOfWeek = firstDayOfWeekOverride ?: firstDayOfWeekFromLocale()
    // anchorMonth is the pager origin (page 0 = anchorMonth). Recomputing it
    // when state.today drifts would force the pager to re-key and lose the
    // user's swipe history; the InitialPage offset compensates by re-mapping
    // the user's current page in showMonth() / pageToYearMonth() math.
    val anchorMonth = remember { YearMonth.from(state.today) }
    val pagerState = rememberPagerState(initialPage = InitialPage) { PageCount }

    val locale = LocalConfiguration.current.locales.get(0)
    val titleFmt = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    LaunchedEffect(pagerState.currentPage) {
        val month = pageToYearMonth(pagerState.currentPage, anchorMonth)
        vm.showMonth(month)
        onViewedMonthChange(month)
        onTitleChange(month.format(titleFmt))
        // The FAB defaults a new event's date to whatever the user is
        // looking at. For Month: today if today falls in the visible
        // month, otherwise the 1st of that month.
        val viewedDate = if (YearMonth.from(state.today) == month) {
            state.today
        } else {
            month.atDay(1)
        }
        onViewedDateChange(viewedDate)
    }

    val jumpCounter by todayJumpCounter.collectAsStateWithLifecycle()
    var lastHandledJump by remember { mutableIntStateOf(jumpCounter) }
    LaunchedEffect(jumpCounter) {
        if (jumpCounter != lastHandledJump) {
            lastHandledJump = jumpCounter
            pagerState.animateScrollToPage(InitialPage)
        }
    }

    // Cross-view target from the header dropdown's month chips or any
    // other caller of requestJumpTo. Clamp to the available page range
    // (240 months total); requests outside that window land on the edge.
    // Filter by target view so jumps meant for Day / Week / Schedule
    // don't trigger this Month-pager scroll (and a premature consume).
    val pendingJump by pendingDateJump.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJump, anchorMonth) {
        val jump = pendingJump?.takeIf { it.view == CalendarView.Month } ?: return@LaunchedEffect
        val targetMonth = YearMonth.from(jump.date)
        val offset = ChronoUnit.MONTHS.between(anchorMonth, targetMonth).toInt()
        val target = (InitialPage + offset).coerceIn(0, PageCount - 1)
        pagerState.animateScrollToPage(target)
        onConsumePendingDateJump()
    }

    Column(modifier = modifier.fillMaxSize()) {
        WeekdayHeader(firstDayOfWeek = firstDayOfWeek, showWeekNumberColumn = showWeekNumber)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            flingBehavior = rememberCalendarPagerFling(pagerState),
        ) { page ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val rowHeight = (maxHeight / 6).coerceAtLeast(WeekRowHeightMin)
                MonthGrid(
                    yearMonth = pageToYearMonth(page, anchorMonth),
                    firstDayOfWeek = firstDayOfWeek,
                    eventsByDate = state.eventsByDate,
                    allEvents = state.events,
                    today = state.today,
                    dimPastDates = dimPastDates,
                    showWeekNumber = showWeekNumber,
                    weekRowHeight = rowHeight,
                    onDayCellClick = onDayCellClick,
                    onOverflowClick = { date -> overflowDate = date },
                )
            }
        }
    }

    overflowDate?.let { date ->
        DayOverflowSheet(
            date = date,
            events = state.eventsByDate[date].orEmpty(),
            onDismiss = { overflowDate = null },
            onEventClick = onEventClick,
        )
    }
}

private fun pageToYearMonth(page: Int, anchor: YearMonth): YearMonth = anchor.plusMonths((page - InitialPage).toLong())
