/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.theme.CalendarTokens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private const val WeekColumns = 7
private const val WeekRows = 6
private const val MaxDotsPerCell = 3

@Composable
internal fun MiniMonthPanel(
    displayedMonth: YearMonth,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    eventsByDate: Map<LocalDate, List<EventItem>>,
    onSelectDate: (LocalDate) -> Unit,
    onShiftMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales.get(0)
    val titleFmt = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        MonthNavRow(
            label = titleFmt.format(displayedMonth),
            onPrev = { onShiftMonth(-1) },
            onNext = { onShiftMonth(1) },
        )
        Spacer(modifier = Modifier.height(4.dp))
        WeekdayHeader(firstDayOfWeek = firstDayOfWeek, locale = locale)
        Spacer(modifier = Modifier.height(2.dp))
        DateGrid(
            displayedMonth = displayedMonth,
            today = today,
            firstDayOfWeek = firstDayOfWeek,
            eventsByDate = eventsByDate,
            onSelectDate = onSelectDate,
        )
    }
}

@Composable
private fun MonthNavRow(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_mini_month_prev),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cd_mini_month_next),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun WeekdayHeader(firstDayOfWeek: DayOfWeek, locale: java.util.Locale) {
    val order = remember(firstDayOfWeek) {
        (0 until WeekColumns).map { i -> firstDayOfWeek.plus(i.toLong()) }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        order.forEach { dow ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = dow.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DateGrid(
    displayedMonth: YearMonth,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    eventsByDate: Map<LocalDate, List<EventItem>>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val firstOfMonth = displayedMonth.atDay(1)
    val leadOffset = ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + WeekColumns) % WeekColumns
    val gridStart = firstOfMonth.minusDays(leadOffset.toLong())
    val totalCells = WeekColumns * WeekRows
    val dates = remember(displayedMonth, firstDayOfWeek) {
        List(totalCells) { i -> gridStart.plusDays(i.toLong()) }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (row in 0 until WeekRows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until WeekColumns) {
                    val date = dates[row * WeekColumns + col]
                    DateCell(
                        date = date,
                        isInMonth = YearMonth.from(date) == displayedMonth,
                        isToday = date == today,
                        events = eventsByDate[date].orEmpty(),
                        onClick = { onSelectDate(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DateCell(
    date: LocalDate,
    isInMonth: Boolean,
    isToday: Boolean,
    events: List<EventItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eventsLabel = if (events.isNotEmpty()) {
        pluralStringResource(R.plurals.mini_month_events_count, events.size, events.size)
    } else {
        null
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (eventsLabel != null) stateDescription = eventsLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isToday) CalendarTokens.todayHighlight else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isToday -> CalendarTokens.onTodayHighlight
                        !isInMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            DotRow(events = events)
        }
    }
}

@Composable
private fun DotRow(events: List<EventItem>) {
    // Group by calendar so days with many events on one calendar stay
    // single-dot. Take the first three distinct calendars in insertion
    // order. Render colored dots; ellipsis-like density beyond that is
    // intentional (the underlying view shows the rest).
    val dotColors = remember(events) {
        events
            .groupBy { it.calendarId }
            .keys
            .toList()
            .take(MaxDotsPerCell)
            .mapNotNull { calId ->
                events.firstOrNull { it.calendarId == calId }?.displayColor
            }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        dotColors.forEach { argb ->
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Color(argb)),
            )
        }
        // Reserve vertical room so cells stay the same height whether or
        // not they have dots. 4.dp dot + spacing matches DotRow's max.
        if (dotColors.isEmpty()) {
            Spacer(modifier = Modifier.size(4.dp))
        }
    }
}
