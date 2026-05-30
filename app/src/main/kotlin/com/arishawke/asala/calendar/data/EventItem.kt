/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import android.provider.CalendarContract
import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Immutable
data class EventItem(
    val instanceId: Long,
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val displayColor: Int,
    // RFC 5545 STATUS property. Defaults to CONFIRMED so existing code
    // paths and tests that construct EventItem without the column see
    // the same rendering as before. The chip renderer treats null /
    // confirmed identically.
    val status: Int = CalendarContract.Events.STATUS_CONFIRMED,
    // Set by the data layer via BirthdayDetection against the source
    // calendar's display name. Drives the cake leading-icon on Block
    // and Row chip variants and on the detail-sheet title row.
    val isBirthday: Boolean = false,
) {
    // CalendarContract stores all-day events with start/end at 00:00 UTC
    // regardless of the device timezone. Interpreting those millis in the
    // device's local zone shifts the date by the UTC offset and lands
    // events on the wrong day in non-UTC zones (e.g., a Feb 1 all-day in
    // UTC-5 reads as Jan 31 if naively zone-converted). Use UTC for the
    // all-day path; timed events keep the device zone.
    private fun effectiveZone(zone: ZoneId): ZoneId = if (allDay) ZoneOffset.UTC else zone

    fun startDate(zone: ZoneId): LocalDate = java.time.Instant
        .ofEpochMilli(startMillis)
        .atZone(effectiveZone(zone))
        .toLocalDate()

    fun endDate(zone: ZoneId): LocalDate = java.time.Instant
        .ofEpochMilli(endMillis)
        .atZone(effectiveZone(zone))
        .toLocalDate()

    // CalendarContract stores all-day endMillis as the start of the day
    // AFTER the last visible day (exclusive). Timed events use inclusive
    // end. Callers that filter "is this event visible on/in this date
    // range" need this distinction or all-day events bleed onto the
    // following day.
    fun isVisibleIn(first: LocalDate, last: LocalDate, zone: ZoneId): Boolean {
        val s = startDate(zone)
        val e = endDate(zone)
        if (s.isAfter(last)) return false
        return if (allDay) e.isAfter(first) else !e.isBefore(first)
    }
}

// Color override application with the precedence stack event > calendar >
// default. Both maps are read-only snapshots from DataStore. For local
// calendars Instances already serves the updated CALENDAR_COLOR through
// DISPLAY_COLOR, so the calendar-level remap is a no-op there. For synced
// calendars (and for every per-event override) the provider is deliberately
// not written, so this remap is the only place the override becomes
// visible on chips. Per-event override is keyed on eventId, not
// instanceId, so it applies to every instance of a recurring event.
fun List<EventItem>.applyColorOverrides(
    calendarOverrides: Map<Long, Int>,
    eventOverrides: Map<Long, Int>,
): List<EventItem> {
    if (calendarOverrides.isEmpty() && eventOverrides.isEmpty()) return this
    return map { e ->
        val override = eventOverrides[e.eventId] ?: calendarOverrides[e.calendarId]
        if (override != null) e.copy(displayColor = override) else e
    }
}

// Composes the chip-render pipeline used by every event-loading view
// model: drop hidden calendars, then apply per-event > per-calendar
// color overrides. Pure; testable in isolation.
fun List<EventItem>.filteredAndRecolored(
    hidden: Set<Long>,
    calendarOverrides: Map<Long, Int>,
    eventOverrides: Map<Long, Int>,
): List<EventItem> = filter { it.calendarId !in hidden }
    .applyColorOverrides(calendarOverrides, eventOverrides)
