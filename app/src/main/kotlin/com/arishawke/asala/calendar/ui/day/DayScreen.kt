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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.components.BirthdayLeadingIcon
import com.arishawke.asala.calendar.ui.settings.containsWorkingDay
import com.arishawke.asala.calendar.ui.theme.rememberCalendarPagerFling
import com.arishawke.asala.calendar.ui.timeline.DayClippedEvent
import com.arishawke.asala.calendar.ui.timeline.HourAxis
import com.arishawke.asala.calendar.ui.timeline.HourHeight
import com.arishawke.asala.calendar.ui.timeline.clipToDay
import com.arishawke.asala.calendar.ui.timeline.rememberNowMinutes
import com.arishawke.asala.calendar.ui.week.DayColumn
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

private const val AllDayLuminanceMidpoint = 0.5f

@Composable
// Same shape as WeekScreen: pager state + today jump + cross-view jump
// + render dispatch, plus the polish-sprint workingHours / workingDays
// params, push this just over detekt's 60-line LongMethod default.
@Suppress("LongParameterList", "LongMethod")
fun DayScreen(
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

    val pageCount = (DayViewModel.WindowDaysEachSide * 2 + 1).toInt()
    val todayPageIndex = DayViewModel.WindowDaysEachSide.toInt()
    val pagerState = rememberPagerState(initialPage = todayPageIndex) { pageCount }

    // Push the visible page's date into the ViewModel so the title and event filter follow.
    LaunchedEffect(pagerState, state.today) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val date = state.today.plusDays((page - todayPageIndex).toLong())
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
            pagerState.animateScrollToPage(todayPageIndex)
        }
    }

    // Targeted jump from another screen (e.g., tapping a month-view cell).
    // Clamp to the pager window; dates outside ±WindowDaysEachSide land on
    // the edge. Filtered by target view so a jump to Week / Month /
    // Schedule doesn't trigger this Day-pager scroll (and a premature
    // consume that would steal the jump from the actual destination).
    val pendingJump by pendingDateJump.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJump, state.today) {
        val jump = pendingJump?.takeIf { it.view == CalendarView.Day } ?: return@LaunchedEffect
        val daysFromToday = ChronoUnit.DAYS.between(state.today, jump.date).toInt()
        val target = (todayPageIndex + daysFromToday).coerceIn(0, pageCount - 1)
        pagerState.animateScrollToPage(target)
        onConsumePendingDateJump()
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        flingBehavior = rememberCalendarPagerFling(pagerState),
    ) { page ->
        val pageDate = state.today.plusDays((page - todayPageIndex).toLong())
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
) {
    val zone = remember { ZoneId.systemDefault() }
    val isToday = date == today

    val dayEvents = remember(events, date) {
        events.filter { it.isVisibleIn(date, date, zone) }
    }
    val allDay = dayEvents.filter { it.allDay }
    // Timed events are clipped to the visible day so a midnight-crosser
    // shows one chip per covered day instead of only on its start day.
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
            // Non-working day supersedes working-hours dim so the column
            // doesn't double-dim.
            workingHoursEnabled = workingHoursEnabled && !isNonWorkingDay,
            workingHoursStartHour = workingHoursStartHour,
            workingHoursEndHour = workingHoursEndHour,
            isNonWorkingDay = isNonWorkingDay,
            onEventClick = onEventClick,
            onReschedule = onReschedule,
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
                // Pale calendar swatches (Okabe-Ito yellow, Radix amber 9)
                // fail WCAG 1.4.3 against white text; pick black on bright
                // backgrounds via the same midpoint MultiDayBarRow uses.
                val rowFg =
                    if (Color(ev.displayColor).luminance() < AllDayLuminanceMidpoint) {
                        Color.White
                    } else {
                        Color.Black
                    }
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
@Suppress("LongParameterList")
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
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val initialHour = if (isToday) {
        max(LocalTime.now(zone).hour - 1, 0)
    } else {
        7
    }
    LaunchedEffect(date) {
        val px = with(density) { (HourHeight.toPx() * initialHour).toInt() }
        scrollState.scrollTo(px)
    }

    val nowMinutes = rememberNowMinutes(zone = zone, enabled = isToday)

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
                modifier = Modifier.weight(1f),
            )
        }
    }
}
