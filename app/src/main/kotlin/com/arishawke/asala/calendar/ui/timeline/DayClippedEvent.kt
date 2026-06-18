/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

import androidx.compose.runtime.Immutable
import com.arishawke.asala.calendar.data.EventItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// A timed event projected onto one day: display millis clipped to
// [day start, next day start) so a midnight-crossing event renders one chip
// per day. continued flags mark cut edges for square corners; event stays the
// original so clicks keep the true id/start; segmentIndex/Count drive the
// "N/total" continuation badge.
@Immutable
internal data class DayClippedEvent(
    val event: EventItem,
    val displayStartMillis: Long,
    val displayEndMillis: Long,
    val continuedFromPrev: Boolean,
    val continuedToNext: Boolean,
    val segmentIndex: Int,
    val segmentCount: Int,
)

// null when the event doesn't touch the day. all-day handled by WeekBucketer.
internal fun clipToDay(event: EventItem, date: LocalDate, zone: ZoneId): DayClippedEvent? {
    if (event.allDay) return null
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val nextDayStart = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    if (event.endMillis <= dayStart || event.startMillis >= nextDayStart) return null
    val startDay = Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalDate()
    // -1ms keeps an event ending at midnight on the prior day, matching the
    // null-boundary above: a 00:00 end does not occupy the new day.
    val lastDay = Instant.ofEpochMilli(maxOf(event.endMillis - 1, event.startMillis))
        .atZone(zone)
        .toLocalDate()
    return DayClippedEvent(
        event = event,
        displayStartMillis = maxOf(event.startMillis, dayStart),
        displayEndMillis = minOf(event.endMillis, nextDayStart),
        continuedFromPrev = event.startMillis < dayStart,
        continuedToNext = event.endMillis > nextDayStart,
        segmentIndex = (date.toEpochDay() - startDay.toEpochDay()).toInt() + 1,
        segmentCount = (lastDay.toEpochDay() - startDay.toEpochDay()).toInt() + 1,
    )
}

// only the first piece is a drag-reschedule anchor. a continuation piece shares
// the event's real start on an earlier column, so dragging it would clamp the
// day-delta against the wrong column (pushing the start off the visible week) and
// hold its offset against the wrong row; reschedule is wired to the first piece.
internal fun DayClippedEvent.isRescheduleAnchor(): Boolean = !continuedFromPrev

// time to show on a multi-day piece: real start on the first, real end on the
// last, null for single-day or middle pieces (which show only the badge).
internal fun segmentAnchorMillis(clip: DayClippedEvent): Long? = when {
    clip.segmentCount <= 1 -> null
    clip.segmentIndex == 1 -> clip.event.startMillis
    clip.segmentIndex == clip.segmentCount -> clip.event.endMillis
    else -> null
}

// clips timed events per day once so callers memoize a stable per-day map.
// allDay filter is the contract: all-day never reaches the timed grid.
internal fun clipEventsByDay(
    events: List<EventItem>,
    days: List<LocalDate>,
    zone: ZoneId,
): Map<LocalDate, List<DayClippedEvent>> {
    val timed = events.filter { !it.allDay }
    return days.associateWith { d -> timed.mapNotNull { clipToDay(it, d, zone) } }
}
