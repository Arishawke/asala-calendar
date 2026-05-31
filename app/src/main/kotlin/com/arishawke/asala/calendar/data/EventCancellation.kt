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

// deletes one occurrence via a zero-duration STATUS_CANCELED exception row.
// gotchas: DTSTART + DTEND required on every insert; CALENDAR_ID must match
// the parent (provider won't infer it); all-day parents need ORIGINAL_ALL_DAY=1,
// ALL_DAY=1, EVENT_TIMEZONE=UTC to match the recurrence engine's UTC-midnight slot.
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

// mirrors EventDraft.toContentValues so map payloads stay unit-testable as
// plain Kotlin, crossing to ContentValues only at the provider edge.
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
