/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.year

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.theme.CalendarTokens
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val MaxDotsPerCell = 3

@Composable
internal fun YearDayCell(
    day: CalendarDay,
    today: LocalDate,
    events: List<EventItem>,
    locale: Locale,
    onClick: () -> Unit,
) {
    val inMonth = day.position == DayPosition.MonthDate
    val isToday = inMonth && day.date == today
    // year view shows only a bare day number; name the full date for TalkBack
    // and fold today / event-count into the state, mirroring the mini-month cell.
    val dateCd = remember(day.date, locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(day.date)
    }
    val todayLabel = if (isToday) stringResource(R.string.state_day_today) else null
    val countLabel = if (inMonth && events.isNotEmpty()) {
        pluralStringResource(R.plurals.mini_month_events_count, events.size, events.size)
    } else {
        null
    }
    val stateLabel = listOfNotNull(todayLabel, countLabel).joinToString(", ").ifEmpty { null }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(enabled = inMonth, role = Role.Button, onClick = onClick)
            .then(
                if (inMonth) {
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = dateCd
                        if (stateLabel != null) stateDescription = stateLabel
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isToday) CalendarTokens.todayHighlight else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isToday -> CalendarTokens.onTodayHighlight
                        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            YearDotRow(events = if (inMonth) events else emptyList())
        }
    }
}

@Composable
private fun YearDotRow(events: List<EventItem>) {
    // one dot per distinct calendar, up to three, matching the mini-month
    // header look. reserve the row height even when empty so cells align.
    val dotColors = remember(events) {
        events.groupBy { it.calendarId }.keys.toList()
            .take(MaxDotsPerCell)
            .mapNotNull { calId -> events.firstOrNull { it.calendarId == calId }?.displayColor }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        dotColors.forEach { argb ->
            Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Color(argb)))
        }
        if (dotColors.isEmpty()) {
            Box(modifier = Modifier.size(3.dp))
        }
    }
}

@Composable
internal fun YearMonthHeader(yearMonth: YearMonth, onClick: () -> Unit, locale: Locale) {
    val fmt = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }
    Text(
        text = fmt.format(yearMonth),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

@Composable
internal fun YearHeaderLabel(year: Int) {
    Text(
        text = year.toString(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
