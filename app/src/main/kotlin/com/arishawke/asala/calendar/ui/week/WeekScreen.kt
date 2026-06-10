/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.week

import android.icu.text.DateIntervalFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arishawke.asala.calendar.AsalaCalendarApplication
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.PendingDateJump
import com.arishawke.asala.calendar.PendingEventReveal
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.settings.containsWorkingDay
import com.arishawke.asala.calendar.ui.theme.rememberCalendarPagerFling
import com.arishawke.asala.calendar.ui.timeline.HourAxisWidth
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.coroutines.flow.StateFlow
import java.text.FieldPosition
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private const val WindowWeeksEachSide = 260

@Composable
// LongMethod: discrete concerns (pager state, today jump, cross-view jump,
// render); helpers would just shift verbosity to the call site.
@Suppress("LongParameterList", "LongMethod")
fun WeekScreen(
    hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    onTitleChange: (String) -> Unit,
    todayJumpCounter: StateFlow<Int>,
    pendingDateJump: StateFlow<PendingDateJump?>,
    onConsumePendingDateJump: () -> Unit,
    pendingEventReveal: StateFlow<PendingEventReveal?>,
    onConsumeEventReveal: () -> Unit,
    modifier: Modifier = Modifier,
    firstDayOfWeekOverride: DayOfWeek? = null,
    dimPastDates: Boolean = false,
    workingHoursEnabled: Boolean = false,
    workingHoursStartHour: Int = 9,
    workingHoursEndHour: Int = 17,
    workingDaysEnabled: Boolean = false,
    workingDaysMask: Long = 0L,
    showWeekNumber: Boolean = false,
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit = { _, _ -> },
    onReschedule: (eventId: Long, instanceMillis: Long, newStartMillis: Long) -> Unit = { _, _, _ -> },
    onViewedDateChange: (LocalDate) -> Unit = {},
) {
    val context = LocalContext.current
    val todayFlow = (context.applicationContext as AsalaCalendarApplication).todayProvider.today
    val vm: WeekViewModel = viewModel(
        factory = WeekViewModel.Factory(
            context.contentResolver,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
            todayFlow,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    val firstDayOfWeek = firstDayOfWeekOverride ?: firstDayOfWeekFromLocale()
    val today = state.today
    val todayWeekStart = remember(today, firstDayOfWeek) {
        today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    }

    val pageCount = WindowWeeksEachSide * 2 + 1
    val todayPageIndex = WindowWeeksEachSide
    val pagerState = rememberPagerState(initialPage = todayPageIndex) { pageCount }

    val locale = LocalConfiguration.current.locales.get(0)
    LaunchedEffect(pagerState, todayWeekStart) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val weekStart = todayWeekStart.plusWeeks((page - todayPageIndex).toLong())
            vm.showWeek(weekStart)
            val weekEnd = weekStart.plusDays(6)
            onTitleChange(formatWeekRange(weekStart, weekEnd, locale))
            // FAB default: today if visible, else week start so a future add lands inside.
            val viewedDate = if (today in weekStart..weekEnd) today else weekStart
            onViewedDateChange(viewedDate)
        }
    }

    // Jump-to-today
    val jumpCounter by todayJumpCounter.collectAsStateWithLifecycle()
    var lastHandledJump by remember { mutableIntStateOf(jumpCounter) }
    LaunchedEffect(jumpCounter) {
        if (jumpCounter != lastHandledJump) {
            lastHandledJump = jumpCounter
            pagerState.animateScrollToPage(todayPageIndex)
        }
    }

    // cross-view jump from the mini-month. filtered by target view so a jump
    // meant for another view doesn't scroll this pager.
    val pendingJump by pendingDateJump.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJump, todayWeekStart) {
        val jump = pendingJump?.takeIf { it.view == CalendarView.Week } ?: return@LaunchedEffect
        val targetWeekStart = jump.date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val offset = java.time.temporal.ChronoUnit.WEEKS.between(todayWeekStart, targetWeekStart).toInt()
        val target = (todayPageIndex + offset).coerceIn(0, pageCount - 1)
        pagerState.animateScrollToPage(target)
        onConsumePendingDateJump()
    }

    // reveal a just-saved event: page to its week, then the destination page's
    // grid scrolls/pills and consumes. filtered to this view.
    val reveal by pendingEventReveal.collectAsStateWithLifecycle()
    LaunchedEffect(reveal, todayWeekStart) {
        val r = reveal?.takeIf { it.view == CalendarView.Week } ?: return@LaunchedEffect
        val targetWeekStart = r.date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val offset = java.time.temporal.ChronoUnit.WEEKS.between(todayWeekStart, targetWeekStart).toInt()
        val target = (todayPageIndex + offset).coerceIn(0, pageCount - 1)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }

    val zone = remember { ZoneId.systemDefault() }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        flingBehavior = rememberCalendarPagerFling(pagerState),
    ) { page ->
        val weekStart = remember(page, todayWeekStart, todayPageIndex) {
            todayWeekStart.plusWeeks((page - todayPageIndex).toLong())
        }
        val days = remember(weekStart) {
            (0..6).map { weekStart.plusDays(it.toLong()) }
        }
        val pageReveal = reveal?.takeIf { it.view == CalendarView.Week && it.date in days }
        WeekPage(
            days = days,
            today = today,
            events = state.events,
            zone = zone,
            dimPastDates = dimPastDates,
            workingHoursEnabled = workingHoursEnabled,
            workingHoursStartHour = workingHoursStartHour,
            workingHoursEndHour = workingHoursEndHour,
            workingDaysEnabled = workingDaysEnabled,
            workingDaysMask = workingDaysMask,
            showWeekNumber = showWeekNumber,
            onEventClick = onEventClick,
            onReschedule = onReschedule,
            reveal = pageReveal,
            onConsumeReveal = onConsumeEventReveal,
        )
    }
}

@Composable
// LongMethod: WeekPage straddles header + all-day + timeline; splitting
// just moves the parameter list elsewhere.
@Suppress("LongParameterList", "LongMethod")
internal fun WeekPage(
    days: List<LocalDate>,
    today: LocalDate,
    events: List<EventItem>,
    zone: ZoneId,
    dimPastDates: Boolean,
    workingHoursEnabled: Boolean,
    workingHoursStartHour: Int,
    workingHoursEndHour: Int,
    workingDaysEnabled: Boolean,
    workingDaysMask: Long,
    showWeekNumber: Boolean,
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit,
    onReschedule: (eventId: Long, instanceMillis: Long, newStartMillis: Long) -> Unit,
    enableOverflow: Boolean = true,
    reveal: PendingEventReveal? = null,
    onConsumeReveal: () -> Unit = {},
) {
    val visibleEvents = remember(days, events) {
        events.filter { it.isVisibleIn(days.first(), days.last(), zone) }
    }
    val (allDayEvents, timedEvents) = remember(visibleEvents) { visibleEvents.partition { it.allDay } }

    Column(modifier = Modifier.fillMaxSize()) {
        // leading slot matches hour-axis width so headers align with columns;
        // shows the ISO week number when enabled, else a spacer.
        Row(modifier = Modifier.fillMaxWidth()) {
            if (showWeekNumber) {
                val isoMonday = days.first().with(java.time.DayOfWeek.MONDAY)
                val weekNumber = isoMonday.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
                Box(
                    modifier = Modifier.width(HourAxisWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = weekNumber.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(HourAxisWidth))
            }
            days.forEach { date ->
                WeekDayHeader(
                    date = date,
                    isToday = date == today,
                    isPast = dimPastDates && date.isBefore(today),
                    isNonWorkingDay = workingDaysEnabled && !workingDaysMask.containsWorkingDay(date.dayOfWeek),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (allDayEvents.isNotEmpty()) {
            AllDayRow(
                events = allDayEvents,
                days = days,
                zone = zone,
                onEventClick = onEventClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        TimelineGrid(
            days = days,
            today = today,
            events = timedEvents,
            zone = zone,
            dimPastDates = dimPastDates,
            workingHoursEnabled = workingHoursEnabled,
            workingHoursStartHour = workingHoursStartHour,
            workingHoursEndHour = workingHoursEndHour,
            workingDaysEnabled = workingDaysEnabled,
            workingDaysMask = workingDaysMask,
            enableOverflow = enableOverflow,
            onEventClick = onEventClick,
            onReschedule = onReschedule,
            reveal = reveal,
            onConsumeReveal = onConsumeReveal,
        )
    }
}

// ICU DateIntervalFormat with a "yMMMd" skeleton so field order, separator,
// and abbreviation track the locale rather than a hardcoded US pattern.
internal fun formatWeekRange(first: LocalDate, last: LocalDate, locale: Locale): String {
    val zone = TimeZone.getDefault()
    val startCal = Calendar.getInstance(zone, locale).apply {
        clear()
        set(first.year, first.monthValue - 1, first.dayOfMonth)
    }
    val endCal = Calendar.getInstance(zone, locale).apply {
        clear()
        set(last.year, last.monthValue - 1, last.dayOfMonth)
    }
    val fmt = DateIntervalFormat.getInstance("yMMMd", locale)
    return fmt.format(startCal, endCal, StringBuffer(), FieldPosition(0)).toString()
}
