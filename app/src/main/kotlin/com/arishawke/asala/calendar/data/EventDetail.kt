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

@Immutable
data class EventDetail(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val eventTimezone: String,
    val rrule: String?,
    val displayColor: Int,
    val calendarDisplayName: String,
    val reminderMinutesBefore: Int?,
    val status: Int = CalendarContract.Events.STATUS_CONFIRMED,
    // BUSY/FREE/TENTATIVE. carried through edits so a server-set value
    // (e.g. DAVx5) isn't clobbered by a local title edit.
    val availability: Int = CalendarContract.Events.AVAILABILITY_BUSY,
    val isBirthday: Boolean = false,
)

// chip color precedence: event > calendar > default.
fun resolveEventDetailColor(
    detail: EventDetail,
    eventOverrides: Map<Long, Int>,
    calendarOverrides: Map<Long, Int>,
): Int = eventOverrides[detail.eventId]
    ?: calendarOverrides[detail.calendarId]
    ?: detail.displayColor

// true only when the scope removes the event row entirely. other scopes
// keep the original eventId (this-instance writes an exception on the same
// row; this-and-following splits via UNTIL + a new row), so the override stays.
fun shouldClearEventOverrideOnDelete(scope: RecurringEditScope): Boolean = scope == RecurringEditScope.AllEvents
