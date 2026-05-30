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
    // CalendarContract.Events.AVAILABILITY - BUSY / FREE / TENTATIVE.
    // Carried through the edit path so a Free / Tentative value set on
    // the server (e.g., DAVx5) is not clobbered by a local title edit.
    val availability: Int = CalendarContract.Events.AVAILABILITY_BUSY,
    // Mirror of EventItem.isBirthday; populated by EventDetailReader
    // via BirthdayDetection against calendarDisplayName.
    val isBirthday: Boolean = false,
)

// Resolves the detail-sheet chip color with the precedence event >
// calendar > default. Pulled out of AppViewModelSheetState so it can
// be unit-tested without spinning up a ViewModel.
fun resolveEventDetailColor(
    detail: EventDetail,
    eventOverrides: Map<Long, Int>,
    calendarOverrides: Map<Long, Int>,
): Int = eventOverrides[detail.eventId]
    ?: calendarOverrides[detail.calendarId]
    ?: detail.displayColor

// True only when the delete scope removes the event row entirely. Other
// scopes leave the original eventId intact (this-instance writes an
// exception against the same row; this-and-following truncates the
// series via UNTIL and inserts a NEW row for the post-split series), so
// the per-event override remains relevant.
fun shouldClearEventOverrideOnDelete(scope: RecurringEditScope): Boolean = scope == RecurringEditScope.AllEvents
