/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.PendingDateJump
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val MonthsEitherSide = 60
private const val TotalMonths = MonthsEitherSide * 2 + 1

// Each month occupies two LazyColumn entries: a stickyHeader at even
// indices and the MonthGrid item at odd indices. Scroll APIs and the
// initial-position seed take LazyColumn entry indices, not month
// indices, so callers must convert via these helpers. Failing to do so
// halves the effective position (a scroll to "month 60" lands on entry
// 60 = the 30th month's header). Internal so the regression test can
// pin the conversion.
internal const val LazyEntriesPerMonth = 2

internal fun monthIndexToLazyIndex(monthIdx: Int): Int = monthIdx * LazyEntriesPerMonth

internal fun lazyIndexToMonthIndex(lazyIdx: Int): Int = lazyIdx / LazyEntriesPerMonth

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun ContinuousMonthScreen(
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
    val locale = LocalConfiguration.current.locales.get(0)
    val titleFmt = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }

    // Origin = first item's yearMonth, captured once so the index<->ym
    // mapping stays stable; today rollover does not shift the surface.
    val origin = remember { YearMonth.from(state.today).minusMonths(MonthsEitherSide.toLong()) }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = monthIndexToLazyIndex(MonthsEitherSide),
    )

    // Center month index throttled via derivedStateOf so reads recompute
    // only when the value actually changes, not on every frame of a fling.
    // ContinuousMonthCenter.pick returns a LazyColumn entry index (header
    // or item); divide by 2 to recover the month index.
    val centerMonthIndex by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val visibleItems = info.visibleItemsInfo.map {
                VisibleItem(it.index, it.offset, it.size)
            }
            val pickedLazyIdx = ContinuousMonthCenter.pick(
                items = visibleItems,
                viewportCenter = viewportCenter,
                fallback = monthIndexToLazyIndex(MonthsEitherSide),
            )
            lazyIndexToMonthIndex(pickedLazyIdx)
        }
    }

    LaunchedEffect(listState, origin) {
        snapshotFlow { centerMonthIndex }
            .distinctUntilChanged()
            .collect { monthIdx ->
                val ym = origin.plusMonths(monthIdx.toLong())
                vm.showMonth(ym)
                onViewedMonthChange(ym)
                onTitleChange(ym.format(titleFmt))
                val viewedDate = if (YearMonth.from(state.today) == ym) state.today else ym.atDay(1)
                onViewedDateChange(viewedDate)
            }
    }

    // Today-jump: scroll to today's month index.
    val jumpCounter by todayJumpCounter.collectAsStateWithLifecycle()
    var lastHandledJump by remember { mutableIntStateOf(jumpCounter) }
    LaunchedEffect(jumpCounter) {
        if (jumpCounter != lastHandledJump) {
            lastHandledJump = jumpCounter
            val todayMonthIdx = ChronoUnit.MONTHS
                .between(origin, YearMonth.from(state.today))
                .toInt()
                .coerceIn(0, TotalMonths - 1)
            listState.animateScrollToItem(monthIndexToLazyIndex(todayMonthIdx))
        }
    }

    // Cross-view jump from search results, notification taps, header
    // dropdown chip strip. scrollToItem (synchronous) matches the
    // "show me this date now" intent.
    val pendingJump by pendingDateJump.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJump) {
        val jump = pendingJump?.takeIf { it.view == CalendarView.Month } ?: return@LaunchedEffect
        val targetMonthIdx = ChronoUnit.MONTHS
            .between(origin, YearMonth.from(jump.date))
            .toInt()
            .coerceIn(0, TotalMonths - 1)
        listState.scrollToItem(monthIndexToLazyIndex(targetMonthIdx))
        onConsumePendingDateJump()
    }

    Column(modifier = modifier.fillMaxSize()) {
        WeekdayHeader(firstDayOfWeek = firstDayOfWeek, showWeekNumberColumn = showWeekNumber)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Pair stickyHeader + item per month so the label stays
            // anchored under the weekday header while the user scrolls
            // through that month's grid.
            for (idx in 0 until TotalMonths) {
                val ym = origin.plusMonths(idx.toLong())
                stickyHeader(key = "h-$idx") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = ym.format(titleFmt),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                item(key = idx) {
                    MonthGrid(
                        yearMonth = ym,
                        firstDayOfWeek = firstDayOfWeek,
                        eventsByDate = state.eventsByDate,
                        allEvents = state.events,
                        today = state.today,
                        dimPastDates = dimPastDates,
                        showWeekNumber = showWeekNumber,
                        weekRowHeight = WeekRowHeightMin,
                        onDayCellClick = onDayCellClick,
                        onOverflowClick = { date -> overflowDate = date },
                        selfContained = true,
                    )
                }
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
