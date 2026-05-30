/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arishawke.asala.calendar.AsalaCalendarApplication
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.PendingDateJump
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.TimeUnits
import com.arishawke.asala.calendar.ui.components.EventChipRow
import com.arishawke.asala.calendar.ui.theme.rememberTimeFormatter
import com.arishawke.asala.calendar.ui.timeline.NowLineRow
import com.arishawke.asala.calendar.ui.timeline.rememberNowMinutes
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
// List state + today jump + cross-view jump + render dispatch + now-line
// marker hookup push this past detekt's 60-line LongMethod default.
@Suppress("LongParameterList", "LongMethod")
fun ScheduleScreen(
    hiddenCalendarIdsFlow: StateFlow<Set<Long>>,
    calendarColorOverridesFlow: StateFlow<Map<Long, Int>>,
    eventColorOverridesFlow: StateFlow<Map<Long, Int>>,
    onTitleChange: (String) -> Unit,
    todayJumpCounter: StateFlow<Int>,
    pendingDateJump: StateFlow<PendingDateJump?>,
    onConsumePendingDateJump: () -> Unit,
    modifier: Modifier = Modifier,
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit = { _, _ -> },
    onViewedDateChange: (LocalDate) -> Unit = {},
) {
    val context = LocalContext.current
    val todayFlow = (context.applicationContext as AsalaCalendarApplication).todayProvider.today
    val vm: ScheduleViewModel = viewModel(
        factory = ScheduleViewModel.Factory(
            context.contentResolver,
            hiddenCalendarIdsFlow,
            calendarColorOverridesFlow,
            eventColorOverridesFlow,
            todayFlow,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    val title = stringResource(R.string.view_schedule)
    LaunchedEffect(title) { onTitleChange(title) }
    // Schedule view is a long scroll without a single "viewed date";
    // default the FAB target to today on entry so the new-event flow
    // doesn't carry a stale date from whatever view the user came from.
    LaunchedEffect(state.today) { onViewedDateChange(state.today) }

    val locale = LocalConfiguration.current.locales.get(0)
    val headerFmt = remember(locale) { DateTimeFormatter.ofPattern("EEEE, MMM d", locale) }
    val timeFmt = rememberTimeFormatter()
    val zone = remember { ZoneId.systemDefault() }

    if (state.daysInOrder.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.schedule_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val todayIndex = remember(state.daysInOrder, state.today) {
        val idx = state.daysInOrder.indexOfFirst { !it.isBefore(state.today) }
        if (idx < 0) state.daysInOrder.lastIndex else idx
    }
    LaunchedEffect(todayIndex) {
        listState.scrollToItem(todayIndex)
    }

    val jumpCounter by todayJumpCounter.collectAsStateWithLifecycle()
    var lastHandledJump by remember { mutableIntStateOf(jumpCounter) }
    LaunchedEffect(jumpCounter) {
        if (jumpCounter != lastHandledJump) {
            lastHandledJump = jumpCounter
            listState.animateScrollToItem(todayIndex)
        }
    }

    // Cross-view target from the header dropdown's mini-month. Find the
    // exact date in the agenda when present, otherwise fall back to the
    // next-future day (the same heuristic todayIndex uses). Filtered by
    // target view so jumps to Day / Week / Month aren't accidentally
    // consumed here.
    val pendingJump by pendingDateJump.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJump, state.daysInOrder) {
        val jump = pendingJump?.takeIf { it.view == CalendarView.Schedule } ?: return@LaunchedEffect
        val exact = state.daysInOrder.indexOf(jump.date)
        val target = if (exact >= 0) {
            exact
        } else {
            state.daysInOrder.indexOfFirst { !it.isBefore(jump.date) }
                .let { if (it < 0) state.daysInOrder.lastIndex.coerceAtLeast(0) else it }
        }
        if (target >= 0) listState.animateScrollToItem(target)
        onConsumePendingDateJump()
    }

    // Now-line ticks only when today's section is actually rendered (today
    // has at least one event). If today is missing from the agenda we skip
    // the marker rather than synthesizing an empty section. Pass the tick-
    // driven epoch millis (not just a boolean) into DaySection so its
    // remember scope invalidates each minute and the split index moves.
    val todayHasEvents = state.today in state.rowsByDate
    val nowMinutes = rememberNowMinutes(zone = zone, enabled = todayHasEvents)
    val nowMillis: Long? = if (nowMinutes != null) {
        state.today.atStartOfDay(zone).toInstant().toEpochMilli() + nowMinutes * TimeUnits.MillisPerMinute
    } else {
        null
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        itemsIndexed(
            items = state.daysInOrder,
            key = { _, d -> d.toEpochDay() },
        ) { _, date ->
            val isToday = date == state.today
            DaySection(
                date = date,
                isToday = isToday,
                rows = state.rowsByDate[date].orEmpty(),
                headerText = headerFmt.format(date),
                timeFmt = timeFmt,
                zone = zone,
                onEventClick = onEventClick,
                nowMillis = if (isToday) nowMillis else null,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun DaySection(
    date: LocalDate,
    isToday: Boolean,
    rows: List<ScheduleRow>,
    headerText: String,
    timeFmt: DateTimeFormatter,
    zone: ZoneId,
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit,
    nowMillis: Long? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DayHeader(date = date, isToday = isToday, text = headerText)
        // Split point sits above the first timed row that has not started
        // yet, so in-progress and finished events stay above the line.
        // All-day rows lead the section as a header block and are excluded
        // from the scan. nowMillis non-null implies isToday upstream.
        val splitIndex = if (nowMillis != null) scheduleNowLineIndex(rows, nowMillis) else -1
        rows.forEachIndexed { index, row ->
            if (index == splitIndex) {
                NowLineRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            // displayEvent only adjusts what the row shows: the badge for
            // multi-day all-day rows, the per-day time slice for timed
            // midnight crossers. Click args always carry the ORIGINAL
            // instance id + start millis so the detail sheet opens the
            // true instance.
            val titled = if (row.totalDays > 1) {
                val badge = stringResource(R.string.schedule_day_of_total, row.dayIndex, row.totalDays)
                row.event.copy(title = "${row.event.title} ($badge)")
            } else {
                row.event
            }
            val displayEvent = titled.copy(
                startMillis = row.displayStartMillis,
                endMillis = row.displayEndMillis,
            )
            EventChipRow(
                event = displayEvent,
                timeFmt = timeFmt,
                zone = zone,
                onEventClick = { _, _ -> onEventClick(row.event.eventId, row.event.startMillis) },
            )
        }
        // All timed events have already started today: line sits below the
        // last row. Skip when the day is all-day-only (splitIndex == size
        // because no timed row matched) AND there are no timed rows at all.
        if (nowMillis != null && splitIndex == rows.size && rows.any { !it.event.allDay }) {
            NowLineRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate, isToday: Boolean, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = if (isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
