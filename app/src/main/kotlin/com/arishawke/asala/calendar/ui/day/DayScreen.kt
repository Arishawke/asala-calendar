/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arishawke.asala.calendar.AsalaCalendarApplication
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.PendingDateJump
import com.arishawke.asala.calendar.PendingEventReveal
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.components.BirthdayLeadingIcon
import com.arishawke.asala.calendar.ui.settings.containsWorkingDay
import com.arishawke.asala.calendar.ui.theme.WcagContrast
import com.arishawke.asala.calendar.ui.theme.rememberCalendarPagerFling
import com.arishawke.asala.calendar.ui.timeline.DayClippedEvent
import com.arishawke.asala.calendar.ui.timeline.HourAxis
import com.arishawke.asala.calendar.ui.timeline.HourHeight
import com.arishawke.asala.calendar.ui.timeline.RevealOverlay
import com.arishawke.asala.calendar.ui.timeline.clipToDay
import com.arishawke.asala.calendar.ui.timeline.rememberNowMinutes
import com.arishawke.asala.calendar.ui.timeline.revealTargetPx
import com.arishawke.asala.calendar.ui.week.DayColumn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private const val HighlightClearMs = 2_000L

@Composable
// pager state + jumps + workingHours/workingDays params exceed detekt's
// 60-line LongMethod default.
@Suppress("LongParameterList", "LongMethod")
fun DayScreen(
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
    val vm: DayViewModel = viewModel(
        factory = DayViewModel.Factory(
            context.contentResolver,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
            todayFlow,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = DayPaging.todayPageIndex) { DayPaging.pageCount }

    // push the visible page's date into the vm so title + event filter follow.
    LaunchedEffect(pagerState, state.today) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val date = DayPaging.dateForPage(state.today, page)
            vm.selectDate(date)
            // FAB defaults a new event to the day the user is viewing.
            onViewedDateChange(date)
        }
    }

    val locale = LocalConfiguration.current.locales.get(0)
    val titleFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale) }
    LaunchedEffect(state.selectedDate) {
        onTitleChange(titleFmt.format(state.selectedDate))
    }

    val jumpCounter by todayJumpCounter.collectAsStateWithLifecycle()
    var lastHandledJump by remember { mutableIntStateOf(jumpCounter) }
    LaunchedEffect(jumpCounter) {
        if (jumpCounter != lastHandledJump) {
            lastHandledJump = jumpCounter
            pagerState.animateScrollToPage(DayPaging.todayPageIndex)
        }
    }

    // jump from another screen, clamped to the pager window. filtered by
    // target view so a Week/Month/Schedule jump doesn't scroll here and
    // prematurely consume it from the real destination.
    val pendingJump by pendingDateJump.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJump, state.today) {
        val jump = pendingJump?.takeIf { it.view == CalendarView.Day } ?: return@LaunchedEffect
        pagerState.animateScrollToPage(DayPaging.pageForDate(state.today, jump.date))
        onConsumePendingDateJump()
    }

    val reveal by pendingEventReveal.collectAsStateWithLifecycle()
    LaunchedEffect(reveal, state.today) {
        val r = reveal?.takeIf { it.view == CalendarView.Day } ?: return@LaunchedEffect
        val target = DayPaging.pageForDate(state.today, r.date)
        // page only; the destination page's Timeline scrolls/pills and consumes.
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        flingBehavior = rememberCalendarPagerFling(pagerState),
    ) { page ->
        val pageDate = DayPaging.dateForPage(state.today, page)
        val pageReveal = reveal?.takeIf { it.view == CalendarView.Day && it.date == pageDate }
        DayPage(
            date = pageDate,
            today = state.today,
            events = state.events,
            workingHoursEnabled = workingHoursEnabled,
            workingHoursStartHour = workingHoursStartHour,
            workingHoursEndHour = workingHoursEndHour,
            workingDaysEnabled = workingDaysEnabled,
            workingDaysMask = workingDaysMask,
            onEventClick = onEventClick,
            onReschedule = onReschedule,
            reveal = pageReveal,
            onConsumeReveal = onConsumeEventReveal,
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun DayPage(
    date: LocalDate,
    today: LocalDate,
    events: List<EventItem>,
    workingHoursEnabled: Boolean,
    workingHoursStartHour: Int,
    workingHoursEndHour: Int,
    workingDaysEnabled: Boolean,
    workingDaysMask: Long,
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit,
    onReschedule: (eventId: Long, instanceMillis: Long, newStartMillis: Long) -> Unit,
    reveal: PendingEventReveal? = null,
    onConsumeReveal: () -> Unit = {},
) {
    val zone = remember { ZoneId.systemDefault() }
    val isToday = date == today

    val dayEvents = remember(events, date) {
        events.filter { it.isVisibleIn(date, date, zone) }
    }
    val allDay = remember(dayEvents) { dayEvents.filter { it.allDay } }
    // clip to the day so a midnight-crosser shows one chip per covered day.
    val timed = remember(dayEvents, date) {
        dayEvents.filter { !it.allDay }.mapNotNull { clipToDay(it, date, zone) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (allDay.isNotEmpty()) {
            AllDayList(events = allDay, onEventClick = onEventClick)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        val isNonWorkingDay = workingDaysEnabled && !workingDaysMask.containsWorkingDay(date.dayOfWeek)
        Timeline(
            date = date,
            isToday = isToday,
            events = timed,
            zone = zone,
            // non-working day supersedes working-hours dim to avoid double-dim.
            workingHoursEnabled = workingHoursEnabled && !isNonWorkingDay,
            workingHoursStartHour = workingHoursStartHour,
            workingHoursEndHour = workingHoursEndHour,
            isNonWorkingDay = isNonWorkingDay,
            onEventClick = onEventClick,
            onReschedule = onReschedule,
            reveal = reveal,
            onConsumeReveal = onConsumeReveal,
        )
    }
}

@Composable
private fun AllDayList(events: List<EventItem>, onEventClick: (eventId: Long, instanceMillis: Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        events.forEach { ev ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .padding(vertical = 1.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(ev.displayColor).copy(alpha = 0.85f))
                    .clickable { onEventClick(ev.eventId, ev.startMillis) }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // foreground picked by actual WCAG contrast against the fill.
                val rowFg = Color(WcagContrast.onColor(ev.displayColor))
                Text(
                    text = stringResource(R.string.schedule_all_day),
                    style = MaterialTheme.typography.labelSmall,
                    color = rowFg,
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (ev.isBirthday) {
                    BirthdayLeadingIcon(size = 14.dp, tint = rowFg)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = ev.title.ifBlank { stringResource(R.string.event_no_title) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = rowFg,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
// LongMethod: scroll + highlight state + reveal-aware scroll + overlay share one
// BoxWithConstraints scope; splitting would thread the scope through callees.
@Suppress("LongParameterList", "LongMethod")
private fun Timeline(
    date: LocalDate,
    isToday: Boolean,
    events: List<DayClippedEvent>,
    zone: ZoneId,
    workingHoursEnabled: Boolean,
    workingHoursStartHour: Int,
    workingHoursEndHour: Int,
    isNonWorkingDay: Boolean,
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit,
    onReschedule: (eventId: Long, instanceMillis: Long, newStartMillis: Long) -> Unit,
    reveal: PendingEventReveal? = null,
    onConsumeReveal: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val hourHeightPx = with(density) { HourHeight.toPx() }
    val initialHour = if (isToday) {
        max(LocalTime.now(zone).hour - 1, 0)
    } else {
        7
    }

    var highlightEventId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(highlightEventId) {
        if (highlightEventId != null) {
            delay(HighlightClearMs)
            highlightEventId = null
        }
    }

    // first composition of this day opens on the revealed event if one targets
    // this date (the "different day" landing), else the default hour. keyed on
    // date only, so a same-day reveal does not re-scroll.
    LaunchedEffect(date) {
        val revealTime = reveal?.takeIf { it.date == date }?.time
        val px = if (revealTime != null) {
            revealTargetPx(revealTime, hourHeightPx)
        } else {
            (hourHeightPx * initialHour).toInt()
        }
        scrollState.scrollTo(px)
    }

    val nowMinutes = rememberNowMinutes(zone = zone, enabled = isToday)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportPx = constraints.maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                HourAxis()
                DayColumn(
                    date = date,
                    isToday = isToday,
                    events = events,
                    zone = zone,
                    onEventClick = onEventClick,
                    onReschedule = onReschedule,
                    nowMinutes = nowMinutes,
                    workingHoursEnabled = workingHoursEnabled,
                    workingHoursStartHour = workingHoursStartHour,
                    workingHoursEndHour = workingHoursEndHour,
                    isNonWorkingDay = isNonWorkingDay,
                    showEndTime = true,
                    highlightEventId = highlightEventId,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        RevealOverlay(
            reveal = reveal,
            scrollState = scrollState,
            viewportHeightPx = viewportPx,
            hourHeightPx = hourHeightPx,
            onHighlight = { highlightEventId = it },
            onConsume = onConsumeReveal,
        )
    }
}
