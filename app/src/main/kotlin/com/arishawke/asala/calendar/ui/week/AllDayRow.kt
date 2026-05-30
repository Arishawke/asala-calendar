/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.week

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.multidaybars.LaneAssigner
import com.arishawke.asala.calendar.ui.multidaybars.MultiDayBarRow
import com.arishawke.asala.calendar.ui.multidaybars.WeekBucketer
import com.arishawke.asala.calendar.ui.timeline.HourAxisWidth
import java.time.LocalDate
import java.time.ZoneId

private const val MaxAllDayLanes = 4

@Composable
internal fun AllDayRow(
    events: List<EventItem>,
    days: List<LocalDate>,
    zone: ZoneId,
    onEventClick: ((eventId: Long, instanceMillis: Long) -> Unit)? = null,
) {
    val weekStart = days.first()
    val segments = remember(weekStart, events) {
        LaneAssigner.assignLanes(
            WeekBucketer.bucketize(events, weekStart, zone, includeSingleDay = true),
        )
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .padding(start = HourAxisWidth, top = 4.dp, bottom = 4.dp),
    ) {
        MultiDayBarRow(
            segments = segments,
            rowWidth = maxWidth,
            maxLanes = MaxAllDayLanes,
            onSegmentClick = { eventId ->
                val startMillis = events.firstOrNull { it.eventId == eventId }?.startMillis
                    ?: weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
                onEventClick?.invoke(eventId, startMillis)
            },
        )
    }
}
