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
    // RFC 5545 STATUS; chip renderer treats null/confirmed identically.
    val status: Int = CalendarContract.Events.STATUS_CONFIRMED,
    val isBirthday: Boolean = false,
    val occasion: OccasionKind = OccasionKind.None,
    // parent event's original DTSTART, for computing age from the occurrence year.
    val parentDtStartMillis: Long = 0L,
    val occasionName: String? = null,
    // row carries the app's occasion CUSTOM_APP_URI (see isOwnedOccasionUri);
    // gates the age/ordinal relabel so hand-added rows keep their own titles.
    val isOwnedOccasion: Boolean = false,
) {
    // all-day millis are at 00:00 UTC regardless of device zone; reading them
    // in local zone shifts the date by the offset (Feb 1 in UTC-5 reads Jan 31).
    // so all-day uses UTC; timed keeps the device zone.
    private fun effectiveZone(zone: ZoneId): ZoneId = if (allDay) ZoneOffset.UTC else zone

    fun startDate(zone: ZoneId): LocalDate = java.time.Instant
        .ofEpochMilli(startMillis)
        .atZone(effectiveZone(zone))
        .toLocalDate()

    fun endDate(zone: ZoneId): LocalDate = java.time.Instant
        .ofEpochMilli(endMillis)
        .atZone(effectiveZone(zone))
        .toLocalDate()

    // the last calendar day the event occupies, inclusive. mirrors the
    // clipToDay / expandTimed convention: endMillis-1 so a 00:00 end stays on
    // the prior day (all-day's exclusive end falls out of the same rule), and a
    // malformed end<=start collapses to the start day.
    fun lastDate(zone: ZoneId): LocalDate = java.time.Instant
        .ofEpochMilli(maxOf(endMillis - 1, startMillis))
        .atZone(effectiveZone(zone))
        .toLocalDate()

    // all-day endMillis is exclusive (start of the day after the last visible
    // day); timed end is inclusive. without this split all-day events bleed
    // onto the following day.
    fun isVisibleIn(first: LocalDate, last: LocalDate, zone: ZoneId): Boolean {
        val s = startDate(zone)
        val e = endDate(zone)
        if (s.isAfter(last)) return false
        return if (allDay) e.isAfter(first) else !e.isBefore(first)
    }
}

// color precedence event > calendar > default. for synced calendars and every
// per-event override the provider isn't written, so this remap is the only place
// the override shows on chips. per-event key is eventId, so it hits every instance.
fun List<EventItem>.applyColorOverrides(
    calendarOverrides: Map<Long, Int>,
    eventOverrides: Map<Long, Int>,
): List<EventItem> {
    if (calendarOverrides.isEmpty() && eventOverrides.isEmpty()) return this
    return map { e ->
        val resolved = resolveOverrideColor(e.eventId, e.calendarId, e.displayColor, eventOverrides, calendarOverrides)
        if (resolved != e.displayColor) e.copy(displayColor = resolved) else e
    }
}

// drop cancelled occurrences and hidden calendars, then apply color overrides.
// cancelled instances (e.g. a synced CalDAV/Google cancellation; our own deletes
// no longer write them, they EXDATE the parent) must not linger struck-through.
fun List<EventItem>.filteredAndRecolored(
    hidden: Set<Long>,
    calendarOverrides: Map<Long, Int>,
    eventOverrides: Map<Long, Int>,
): List<EventItem> = filter {
    it.calendarId !in hidden && it.status != CalendarContract.Events.STATUS_CANCELED
}.applyColorOverrides(calendarOverrides, eventOverrides)
