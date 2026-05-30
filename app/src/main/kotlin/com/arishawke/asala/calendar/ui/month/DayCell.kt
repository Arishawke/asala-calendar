/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.theme.PastDateAlpha
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition

// Chip area of a month-view day cell. The day number lives in a separate
// row above the multi-day bars now (see DayNumberBadge), so this cell
// only owns the timed-event chips and the overflow row.
@Composable
@Suppress("LongParameterList")
fun DayCell(
    day: CalendarDay,
    events: List<EventItem>,
    modifier: Modifier = Modifier,
    isPast: Boolean = false,
    onClick: (() -> Unit)? = null,
    onEventClick: ((eventId: Long, instanceMillis: Long) -> Unit)? = null,
    onOverflowClick: (() -> Unit)? = null,
) {
    val isInMonth = day.position == DayPosition.MonthDate
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (isPast) Modifier.alpha(PastDateAlpha) else Modifier)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isInMonth && events.isNotEmpty()) {
            EventChips(
                date = day.date,
                events = events,
                onEventClick = onEventClick,
                onOverflowClick = onOverflowClick,
            )
        }
    }
}
