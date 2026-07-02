/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.header

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.accessibility.rememberAnimationsEnabled
import com.arishawke.asala.calendar.ui.components.distinctCalendarDotColors
import com.arishawke.asala.calendar.ui.theme.CalendarTokens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

private const val WeekColumns = 7
private const val WeekRows = 6

// horizontal travel before a swipe flips the month
private val SwipeThreshold = 48.dp

// touch-target floor for a mini-month cell; content grows past it at larger
// font scale so the dot row is never pushed out.
private val MiniMonthCellFloor = 48.dp
private val MiniMonthBadgeSpacerHeight = 2.dp

// dot diameter; dots are chrome, not text, so they do not scale with font size.
private val MiniMonthDotRowHeight = 4.dp

// breathing room around the digit; 16sp line height + 6dp reproduces the old
// 22dp circle at default scale
private val MiniMonthBadgePadding = 6.dp

// LongMethod: one cohesive panel (title, swipe + a11y actions, animated grid);
// splitting it would scatter the gesture and animation wiring.
@Suppress("LongMethod")
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
    val animationsEnabled by rememberAnimationsEnabled()
    // swipe replaces the prev/next arrows; keep the same actions reachable for
    // TalkBack, which can't perform the swipe.
    val prevLabel = stringResource(R.string.cd_mini_month_prev)
    val nextLabel = stringResource(R.string.cd_mini_month_next)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // swipe left -> next month, right -> previous; taps fall through to
            // the date cells (a tap never crosses the drag slop).
            .pointerInput(Unit) {
                val thresholdPx = SwipeThreshold.toPx()
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        when {
                            totalDrag <= -thresholdPx -> onShiftMonth(1)
                            totalDrag >= thresholdPx -> onShiftMonth(-1)
                        }
                    },
                ) { change, dragAmount ->
                    totalDrag += dragAmount
                    change.consume()
                }
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(prevLabel) {
                        onShiftMonth(-1)
                        true
                    },
                    CustomAccessibilityAction(nextLabel) {
                        onShiftMonth(1)
                        true
                    },
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = titleFmt.format(displayedMonth),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        WeekdayHeader(firstDayOfWeek = firstDayOfWeek, locale = locale)
        Spacer(modifier = Modifier.height(2.dp))
        AnimatedContent(
            targetState = displayedMonth,
            transitionSpec = {
                if (!animationsEnabled) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val forward = targetState > initialState
                    val direction = if (forward) SlideDirection.Left else SlideDirection.Right
                    (fadeIn() + slideIntoContainer(direction)) togetherWith
                        (fadeOut() + slideOutOfContainer(direction))
                }
            },
            label = "mini-month-grid",
        ) { month ->
            DateGrid(
                displayedMonth = month,
                today = today,
                firstDayOfWeek = firstDayOfWeek,
                eventsByDate = eventsByDate,
                onSelectDate = onSelectDate,
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
    // the cell shows only a bare number; name the full date for TalkBack.
    val locale = LocalConfiguration.current.locales.get(0)
    val dateCd = remember(date, locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date)
    }
    val badgeDiameter = with(LocalDensity.current) {
        MaterialTheme.typography.labelMedium.lineHeight.toDp()
    } + MiniMonthBadgePadding
    val cellContentHeight = badgeDiameter + MiniMonthBadgeSpacerHeight + MiniMonthDotRowHeight
    Box(
        modifier = modifier
            .heightIn(min = maxOf(MiniMonthCellFloor, cellContentHeight))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = dateCd
                if (eventsLabel != null) stateDescription = eventsLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(badgeDiameter)
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
            Spacer(modifier = Modifier.height(MiniMonthBadgeSpacerHeight))
            DotRow(events = events)
        }
    }
}

@Composable
private fun DotRow(events: List<EventItem>) {
    // one dot per calendar, first three in insertion order, so a busy day
    // on a single calendar stays single-dot. overflow shows in the view.
    val dotColors = remember(events) { distinctCalendarDotColors(events) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        dotColors.forEach { argb ->
            Box(
                modifier = Modifier
                    .size(MiniMonthDotRowHeight)
                    .clip(CircleShape)
                    .background(Color(argb)),
            )
        }
        // reserve room so cells keep equal height with or without dots.
        if (dotColors.isEmpty()) {
            Spacer(modifier = Modifier.size(MiniMonthDotRowHeight))
        }
    }
}
