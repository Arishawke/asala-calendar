/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import android.content.ContentValues
import android.provider.CalendarContract

data class EventDraft(
    val calendarId: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val eventTimezone: String,
    val rrule: String?,
    // null -> STATUS_CONFIRMED (CalDAV accounts reject rows without it).
    // edits pass the loaded value so a server-set Tentative isn't overwritten.
    val status: Int? = null,
    // same preservation contract as status; insert fallback AVAILABILITY_BUSY.
    val availability: Int? = null,
    // identifies app-owned events (ADR-0002 rule 7: no sync-adapter writes).
    val customAppPackage: String? = null,
    val customAppUri: String? = null,
) {
    // plain map so field mapping is unit-testable without a ContentValues stub.
    internal fun toMap(): Map<String, Any?> = buildMap {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, title.ifBlank { "(No title)" })
        put(CalendarContract.Events.DESCRIPTION, description)
        put(CalendarContract.Events.EVENT_LOCATION, location)
        put(CalendarContract.Events.DTSTART, startMillis)
        put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, eventTimezone)
        // some CalDAV-backed accounts reject inserts without these fields
        put(CalendarContract.Events.STATUS, status ?: CalendarContract.Events.STATUS_CONFIRMED)
        put(CalendarContract.Events.AVAILABILITY, availability ?: CalendarContract.Events.AVAILABILITY_BUSY)
        if (customAppPackage != null) put(CalendarContract.Events.CUSTOM_APP_PACKAGE, customAppPackage)
        if (customAppUri != null) put(CalendarContract.Events.CUSTOM_APP_URI, customAppUri)

        if (rrule != null) {
            // recurring rows need DURATION, not DTEND. some account types reject
            // the PT{n}S seconds-only form, so emit full P{d}DT{h}H{m}M0S.
            put(CalendarContract.Events.RRULE, rrule)
            put(CalendarContract.Events.DURATION, iso8601Duration(endMillis - startMillis, allDay))
        } else {
            put(CalendarContract.Events.DTEND, endMillis)
        }
    }

    fun toContentValues(): ContentValues = toMap().toCalendarEventContentValues()

    private fun iso8601Duration(durationMillis: Long, allDay: Boolean): String {
        // all-day rows store day-form durations. the provider's fixAllDayTime
        // parses any all-day duration ending in 'S' as pure seconds and
        // Integer.parseInt-crashes on a time-component form (P1DT0H0M0S).
        if (allDay) {
            val days = (durationMillis / MillisPerDay).coerceAtLeast(1)
            return "P${days}D"
        }
        val totalSeconds = (durationMillis / 1000).coerceAtLeast(60)
        val days = totalSeconds / 86_400
        val hours = (totalSeconds % 86_400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "P${days}DT${hours}H${minutes}M${seconds}S"
    }

    companion object {
        private const val MillisPerDay = 86_400_000L

        // inverse of iso8601Duration. tolerant of our writes, shorter RFC 5545
        // forms (P1D, PT1H, P1W), and the non-strict P3600S form (no T) that
        // some sync adapters write back. T separator optional regardless of units.
        private val DURATION_RE =
            Regex(
                "^P(?:(\\d+)W)?(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$",
            )

        fun parseIso8601DurationMs(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val m = DURATION_RE.matchEntire(raw) ?: return null
            val (w, d, h, mn, s) = m.destructured
            if (w.isEmpty() && d.isEmpty() && h.isEmpty() && mn.isEmpty() && s.isEmpty()) return null
            val weeks = w.toLongOrNull() ?: 0L
            val days = d.toLongOrNull() ?: 0L
            val hours = h.toLongOrNull() ?: 0L
            val minutes = mn.toLongOrNull() ?: 0L
            val seconds = s.toLongOrNull() ?: 0L
            return ((weeks * 7 + days) * 86_400 + hours * 3600 + minutes * 60 + seconds) * 1000L
        }
    }
}
