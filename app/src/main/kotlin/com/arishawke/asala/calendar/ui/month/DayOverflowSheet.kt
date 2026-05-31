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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.theme.Spacing
import com.arishawke.asala.calendar.ui.theme.rememberTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// keyed on the composition-time locale, not class-load Locale.getDefault(); must match DayCell's overflow cd
@Composable
internal fun rememberOverflowDateFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales.get(0)
    return remember(locale) { DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayOverflowSheet(
    date: LocalDate,
    events: List<EventItem>,
    onDismiss: () -> Unit,
    onEventClick: (eventId: Long, instanceMillis: Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    var isDismissing by remember { mutableStateOf(false) }

    // upstream eventsByDate is unsorted and does not group all-day first
    val (allDay, timed) = events.partition { it.allDay }
    val ordered = allDay + timed.sortedBy { it.startMillis }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        val overflowDateFormatter = rememberOverflowDateFormatter()
        Text(
            text = date.format(overflowDateFormatter),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )

        if (ordered.isEmpty()) {
            Text(
                text = stringResource(R.string.sheet_no_events),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            )
        } else {
            Column {
                ordered.forEach { ev ->
                    EventRow(
                        event = ev,
                        zone = zone,
                        onClick = {
                            if (!isDismissing) {
                                isDismissing = true
                                dismissThenOpen(scope, sheetState, ev, onDismiss, onEventClick)
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun dismissThenOpen(
    scope: CoroutineScope,
    sheetState: SheetState,
    event: EventItem,
    onDismiss: () -> Unit,
    onEventClick: (Long, Long) -> Unit,
) {
    scope.launch {
        // await slide-down so two sheets never overlap
        sheetState.hide()
        onDismiss()
        onEventClick(event.eventId, event.startMillis)
    }
}

@Composable
private fun EventRow(event: EventItem, zone: ZoneId, onClick: () -> Unit) {
    val timeFmt = rememberTimeFormatter()
    val supporting = if (event.allDay) {
        stringResource(R.string.schedule_all_day)
    } else {
        val start = Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalTime()
        val end = Instant.ofEpochMilli(event.endMillis).atZone(zone).toLocalTime()
        stringResource(R.string.time_range_format, start.format(timeFmt), end.format(timeFmt))
    }

    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(event.displayColor)),
            )
        },
        headlineContent = {
            Text(
                text = event.title.ifBlank { stringResource(R.string.event_no_title) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
