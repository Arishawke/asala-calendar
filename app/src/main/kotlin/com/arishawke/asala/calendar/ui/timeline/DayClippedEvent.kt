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

// A timed event projected onto a single day. displayStartMillis /
// displayEndMillis are clipped to [day start, next day start) so an event
// crossing midnight renders one chip per covered day; continuedFromPrev /
// continuedToNext flag the cut edges for square-corner styling. event
// remains the original so click handlers keep the true instance id and
// start millis. segmentIndex / segmentCount number this piece among the
// event's covered days (1/1 for a single-day event) so the chip can show a
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

// Returns null when the event does not touch the day. All-day events are
// not handled here; see WeekBucketer for the all-day bar pipeline.
internal fun clipToDay(event: EventItem, date: LocalDate, zone: ZoneId): DayClippedEvent? {
    if (event.allDay) return null
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val nextDayStart = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    if (event.endMillis <= dayStart || event.startMillis >= nextDayStart) return null
    val startDay = Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalDate()
    // The last instant the event is active. The -1ms keeps an event ending
    // exactly at midnight on the prior day, matching the clip null-boundary
    // above (an event that ends at 00:00 does not occupy the new day).
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

// The single time to show on a multi-day piece: the real start on the first
// piece, the real end on the last, null for a single-day event or a middle
// piece (which fills a whole column and shows only the segment badge).
internal fun segmentAnchorMillis(clip: DayClippedEvent): Long? = when {
    clip.segmentCount <= 1 -> null
    clip.segmentIndex == 1 -> clip.event.startMillis
    clip.segmentIndex == clip.segmentCount -> clip.event.endMillis
    else -> null
}

// Clips timed events to each day once, so callers can memoize a stable
// per-day map instead of re-filtering every column on recomposition.
// The allDay filter is the contract: all-day events are laid out separately,
// never on the timed grid.
internal fun clipEventsByDay(
    events: List<EventItem>,
    days: List<LocalDate>,
    zone: ZoneId,
): Map<LocalDate, List<DayClippedEvent>> {
    val timed = events.filter { !it.allDay }
    return days.associateWith { d -> timed.mapNotNull { clipToDay(it, d, zone) } }
}
