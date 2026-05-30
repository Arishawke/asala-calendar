/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.data.TimeUnits
import com.arishawke.asala.calendar.ui.month.DayOverflowSheet
import com.arishawke.asala.calendar.ui.settings.containsWorkingDay
import com.arishawke.asala.calendar.ui.theme.PastDateAlpha
import com.arishawke.asala.calendar.ui.timeline.CrowdedColumnThreshold
import com.arishawke.asala.calendar.ui.timeline.DayClippedEvent
import com.arishawke.asala.calendar.ui.timeline.DayHeight
import com.arishawke.asala.calendar.ui.timeline.HourAxis
import com.arishawke.asala.calendar.ui.timeline.HourHeight
import com.arishawke.asala.calendar.ui.timeline.NowLineRow
import com.arishawke.asala.calendar.ui.timeline.clipEventsByDay
import com.arishawke.asala.calendar.ui.timeline.crowdedLayout
import com.arishawke.asala.calendar.ui.timeline.rememberNowMinutes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max

@Composable
// LongMethod: the overflow opt-out gate (enableOverflow -> nullable
// callback) pushes this a few lines past detekt's 60-line default. The
// body stays a single render concern; a helper would just relocate it.
@Suppress("LongParameterList", "LongMethod")
internal fun TimelineGrid(
    days: List<LocalDate>,
    today: LocalDate,
    events: List<EventItem>,
    zone: ZoneId,
    dimPastDates: Boolean = false,
    workingHoursEnabled: Boolean = false,
    workingHoursStartHour: Int = 9,
    workingHoursEndHour: Int = 17,
    workingDaysEnabled: Boolean = false,
    workingDaysMask: Long = 0L,
    enableOverflow: Boolean = true,
    onEventClick: ((eventId: Long, instanceMillis: Long) -> Unit)? = null,
    onReschedule: ((eventId: Long, instanceMillis: Long, newStartMillis: Long) -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val showNowLine = today in days
    val initialHour = if (showNowLine) {
        max(LocalTime.now(zone).hour - 1, 0)
    } else {
        7
    }
    LaunchedEffect(Unit) {
        val px = with(density) { (HourHeight.toPx() * initialHour).toInt() }
        scrollState.scrollTo(px)
    }

    val nowMinutes = rememberNowMinutes(zone = zone, enabled = showNowLine)

    var overflowSheet by remember { mutableStateOf<Pair<LocalDate, List<EventItem>>?>(null) }
    // 3-day opts out of crowded overflow (wide columns, like Day). A null
    // callback makes DayColumn use threshold Int.MAX_VALUE: nothing
    // collapses and no "+N" chip is drawn.
    val overflowCallback: ((date: LocalDate, events: List<EventItem>) -> Unit)? =
        if (enableOverflow) {
            { date, evs -> overflowSheet = date to evs }
        } else {
            null
        }

    // Clip events to each day once per data change so columns get stable list
    // references; without this the now-line tick re-filters every column.
    val timedByDay = remember(events, days, zone) { clipEventsByDay(events, days, zone) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            HourAxis(labelOffsetY = (-6).dp)
            days.forEachIndexed { index, date ->
                val isNonWorkingDay = workingDaysEnabled && !workingDaysMask.containsWorkingDay(date.dayOfWeek)
                DayColumn(
                    date = date,
                    isToday = date == today,
                    isPast = dimPastDates && date.isBefore(today),
                    events = timedByDay.getValue(date),
                    zone = zone,
                    weekDayIndex = index,
                    weekDayCount = days.size,
                    onEventClick = onEventClick,
                    onOverflow = overflowCallback,
                    onReschedule = onReschedule,
                    nowMinutes = if (date == today) nowMinutes else null,
                    // A non-working day's full-column dim supersedes the
                    // working-hours band dim so the column doesn't double-dim.
                    workingHoursEnabled = workingHoursEnabled && !isNonWorkingDay,
                    workingHoursStartHour = workingHoursStartHour,
                    workingHoursEndHour = workingHoursEndHour,
                    isNonWorkingDay = isNonWorkingDay,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    overflowSheet?.let { (sheetDate, sheetEvents) ->
        DayOverflowSheet(
            date = sheetDate,
            events = sheetEvents,
            onDismiss = { overflowSheet = null },
            onEventClick = { eventId, instanceMillis ->
                overflowSheet = null
                onEventClick?.invoke(eventId, instanceMillis)
            },
        )
    }
}

// LongMethod + CyclomaticComplexMethod: DayColumn covers three discrete
// concerns (background dims, event layout with overflow, now-line) that
// live here to share a single BoxWithConstraints scope; splitting would
// require threading BoxScope down through multiple callees.
@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
internal fun DayColumn(
    date: LocalDate,
    isToday: Boolean,
    events: List<DayClippedEvent>,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    isPast: Boolean = false,
    weekDayIndex: Int = 0,
    weekDayCount: Int = 1,
    onEventClick: ((eventId: Long, instanceMillis: Long) -> Unit)? = null,
    onReschedule: ((eventId: Long, instanceMillis: Long, newStartMillis: Long) -> Unit)? = null,
    nowMinutes: Int? = null,
    workingHoursEnabled: Boolean = false,
    workingHoursStartHour: Int = 9,
    workingHoursEndHour: Int = 17,
    isNonWorkingDay: Boolean = false,
    showEndTime: Boolean = false,
    onOverflow: ((date: LocalDate, events: List<EventItem>) -> Unit)? = null,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .height(DayHeight)
            .then(if (isPast) Modifier.alpha(PastDateAlpha) else Modifier)
            .padding(horizontal = 1.dp),
    ) {
        HourGuideLines()
        // Dim the non-working bands behind the chips so events stay
        // legible inside and outside the working block. Drawn after the
        // hour lines so it overlays them and before the chip loop so
        // chips render on top of the dim.
        if (workingHoursEnabled) {
            WorkingHoursDim(workingHoursStartHour, workingHoursEndHour)
        }
        // Whole-column dim for non-working days. Drawn at the same alpha
        // as WorkingHoursDim so the two surfaces read identical; suppresses
        // working-hours via the caller so we don't double-dim.
        if (isNonWorkingDay) {
            NonWorkingDayDim()
        }
        val columnWidth = maxWidth
        val columnWidthPx = with(density) { columnWidth.toPx() }
        val threshold = if (onOverflow != null) {
            CrowdedColumnThreshold
        } else {
            Int.MAX_VALUE
        }
        // threshold is a non-layout constant (3 or Int.MAX_VALUE); safe as a remember key.
        val crowded = remember(events, threshold) { crowdedLayout(events, threshold) }
        crowded.visible.forEach { laid ->
            // A crowded cluster keeps only column 0; render it full width.
            // Everything else divides the column as before.
            val isCrowdedPrimary = laid.clusterWidth >= threshold
            val perColumnWidth = if (isCrowdedPrimary) columnWidth else columnWidth / laid.clusterWidth
            val xOffset = if (isCrowdedPrimary) 0.dp else perColumnWidth * laid.columnIndex
            val originalEvent = laid.clipped.event
            EventBlock(
                clipped = laid.clipped,
                zone = zone,
                dayColumnWidthPx = columnWidthPx,
                weekDayIndex = weekDayIndex,
                weekDayCount = weekDayCount,
                showEndTime = showEndTime,
                onClick = onEventClick?.let { cb ->
                    { cb(originalEvent.eventId, originalEvent.startMillis) }
                },
                onReschedule = onReschedule?.let { cb ->
                    { newStart -> cb(originalEvent.eventId, originalEvent.startMillis, newStart) }
                },
                modifier = Modifier
                    .width(perColumnWidth)
                    .offset(x = xOffset),
            )
        }
        crowded.overflow.forEach { group ->
            val yDp = with(density) { (HourHeight.toPx() * group.clusterStartMillis.minutesOfDay(zone) / 60f).toDp() }
            onOverflow?.let { cb ->
                OverflowChip(
                    count = group.collapsedCount,
                    onClick = { cb(date, group.events) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = yDp)
                        .padding(top = 2.dp, end = 2.dp),
                )
            }
        }
        // Now-line sits on top of grid lines but under chip taps. Rendered
        // inside the column so its width tracks the column rather than
        // spanning all seven days.
        if (isToday && nowMinutes != null) {
            val yDp = with(density) { (HourHeight.toPx() * nowMinutes / 60f).toDp() }
            NowLineRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = yDp),
            )
        }
    }
}

@Composable
private fun HourGuideLines() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
    ) {
        repeat(TimeUnits.HoursPerDay) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HourHeight),
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// Two stacked bands that overlay the hours before workingHoursStartHour
// and after workingHoursEndHour. Sits between HourGuideLines and the
// chip loop so the grid lines underneath read as faded while chips drawn
// on top stay full-opacity.
//
// Color is a plain black at low alpha rather than a theme token: an
// earlier surfaceVariant.copy(alpha = 0.5f) only differed from surface
// by ~5-7% in light theme and was invisible in dark / AMOLED (where
// surfaceVariant is pure black on a near-black surface). Constant black
// uniformly darkens both themes and stays visible without overwhelming
// the work block.
// Full-column dim for non-working days. Same Color.Black @ 12% alpha as
// the per-band WorkingHoursDim so the two treatments visually match.
@Composable
private fun NonWorkingDayDim() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.12f)),
    )
}

@Composable
private fun WorkingHoursDim(startHour: Int, endHour: Int) {
    val dim = Color.Black.copy(alpha = 0.12f)
    val safeStart = startHour.coerceIn(0, TimeUnits.MaxStartHour)
    val safeEnd = endHour.coerceIn(safeStart + 1, TimeUnits.HoursPerDay)
    Column(modifier = Modifier.fillMaxSize()) {
        if (safeStart > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HourHeight * safeStart)
                    .background(dim),
            )
        }
        // Spacer for the working block; the dim bands are above and below.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HourHeight * (safeEnd - safeStart)),
        )
        if (safeEnd < TimeUnits.HoursPerDay) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HourHeight * (TimeUnits.HoursPerDay - safeEnd))
                    .background(dim),
            )
        }
    }
}

// Minutes from local midnight for an epoch-millis instant, used to place
// the overflow chip at the top of its cluster.
private fun Long.minutesOfDay(zone: ZoneId): Int {
    val t = Instant.ofEpochMilli(this).atZone(zone).toLocalTime()
    return t.hour * TimeUnits.MinutesPerHour + t.minute
}
