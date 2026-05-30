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

// CalendarContract requires DTSTART plus DTEND-or-DURATION on every Events
// insert. For a "delete this one occurrence" we insert a zero-duration
// exception row marked STATUS_CANCELED so the provider hides that slot.
// CALENDAR_ID must match the parent series; the provider does not infer it.
// For all-day parents: ORIGINAL_ALL_DAY=1, ALL_DAY=1, EVENT_TIMEZONE=UTC are
// required for the provider to match the exception against the UTC-midnight
// slot the recurrence engine produces.
object EventCancellation {
    fun buildMap(
        parentEventId: Long,
        parentCalendarId: Long,
        instanceMillis: Long,
        timezoneId: String,
        parentAllDay: Boolean = false,
    ): Map<String, Any?> = buildMap {
        put(CalendarContract.Events.ORIGINAL_ID, parentEventId)
        put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, instanceMillis)
        put(CalendarContract.Events.ORIGINAL_ALL_DAY, if (parentAllDay) 1 else 0)
        put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
        put(CalendarContract.Events.EVENT_TIMEZONE, if (parentAllDay) "UTC" else timezoneId)
        put(CalendarContract.Events.ALL_DAY, if (parentAllDay) 1 else 0)
        put(CalendarContract.Events.DTSTART, instanceMillis)
        put(CalendarContract.Events.DTEND, instanceMillis)
        put(CalendarContract.Events.CALENDAR_ID, parentCalendarId)
    }
}

// Mirrors EventDraft.toContentValues' converter so map-shaped Events
// payloads can be unit-tested as plain Kotlin and only crossed over to
// ContentValues at the provider edge.
internal fun Map<String, Any?>.toCalendarEventContentValues(): ContentValues {
    val cv = ContentValues()
    forEach { (key, value) ->
        when (value) {
            null -> cv.putNull(key)
            is Long -> cv.put(key, value)
            is Int -> cv.put(key, value)
            is String -> cv.put(key, value)
            else -> cv.put(key, value.toString())
        }
    }
    return cv
}
