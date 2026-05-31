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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.theme.CalendarTokens
import com.arishawke.asala.calendar.ui.theme.PastDateAlpha
import com.arishawke.asala.calendar.ui.theme.Spacing
import java.time.LocalDate

// LongMethod: past/today/non-working decorations share one Column root;
// extracting the `then`-branches would obscure the modifier chain.
@Suppress("LongMethod")
@Composable
internal fun WeekDayHeader(
    date: LocalDate,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    isPast: Boolean = false,
    isNonWorkingDay: Boolean = false,
) {
    val locale = LocalConfiguration.current.locales.get(0)
    val todayLabel = stringResource(R.string.state_day_today)
    val pastLabel = stringResource(R.string.state_day_past)
    val headerState = when {
        isToday -> todayLabel
        isPast -> pastLabel
        else -> null
    }
    Column(
        modifier = modifier
            .then(if (isPast) Modifier.alpha(PastDateAlpha) else Modifier)
            .then(
                if (isNonWorkingDay) {
                    // same 12% black as TimelineGrid dims so the surfaces match.
                    Modifier.background(Color.Black.copy(alpha = 0.12f))
                } else {
                    Modifier
                },
            )
            .then(
                if (headerState != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        stateDescription = headerState
                    }
                } else {
                    Modifier
                },
            )
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.dayOfWeek
                .getDisplayName(java.time.format.TextStyle.SHORT, locale)
                .uppercase(locale),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) {
                CalendarTokens.todayHighlight
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .height(28.dp)
                .width(28.dp)
                .clip(CircleShape)
                .background(
                    if (isToday) CalendarTokens.todayHighlight else Color.Transparent,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (isToday) {
                    CalendarTokens.onTodayHighlight
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}
