/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.theme.CalendarTokens
import com.arishawke.asala.calendar.ui.theme.PastDateAlpha
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition

// The day-number circle that sits above the all-day bars in Month view.
// Extracted from DayCell so MonthGrid can render a header row of numbers,
// then the bar row, then the chips row (events appear below the date).
// Tapping the number column jumps to Day view.
@Composable
internal fun DayNumberBadge(
    day: CalendarDay,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    isPast: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val isInMonth = day.position == DayPosition.MonthDate
    val numberColor = when {
        isToday -> CalendarTokens.onTodayHighlight
        isInMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val todayLabel = stringResource(R.string.state_day_today)
    val pastLabel = stringResource(R.string.state_day_past)
    val cellState = when {
        isToday -> todayLabel
        isPast -> pastLabel
        else -> null
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (isPast) Modifier.alpha(PastDateAlpha) else Modifier)
            .then(
                if (cellState != null) {
                    Modifier.semantics(mergeDescendants = true) { stateDescription = cellState }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isToday) CalendarTokens.todayHighlight else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = numberColor,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
