/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.CalendarContract

// small provider-read helpers shared by the occasion orchestration integration
// tests, so each test class asserts against the real CalendarProvider without
// repeating cursor plumbing.

// event-row ids of the app-owned occasion events in a calendar (rows carrying a
// parseable CUSTOM_APP_URI), so assertions ignore any stray hand-added row.
fun ContentResolver.occasionEventIdsIn(calendarId: Long): List<Long> {
    val ids = mutableListOf<Long>()
    query(
        CalendarContract.Events.CONTENT_URI,
        arrayOf(CalendarContract.Events._ID, CalendarContract.Events.CUSTOM_APP_URI),
        "${CalendarContract.Events.CALENDAR_ID} = ?",
        arrayOf(calendarId.toString()),
        null,
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
        val uriIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.CUSTOM_APP_URI)
        while (cursor.moveToNext()) {
            if (parseOccasionUri(cursor.getString(uriIdx)) != null) ids += cursor.getLong(idIdx)
        }
    }
    return ids
}

fun ContentResolver.reminderMinutesFor(eventId: Long): List<Int> {
    val minutes = mutableListOf<Int>()
    query(
        CalendarContract.Reminders.CONTENT_URI,
        arrayOf(CalendarContract.Reminders.MINUTES),
        "${CalendarContract.Reminders.EVENT_ID} = ?",
        arrayOf(eventId.toString()),
        null,
    )?.use { cursor ->
        while (cursor.moveToNext()) minutes += cursor.getInt(0)
    }
    return minutes
}

fun ContentResolver.titleOf(eventId: Long): String? = query(
    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
    arrayOf(CalendarContract.Events.TITLE),
    null,
    null,
    null,
)?.use { if (it.moveToFirst()) it.getString(0) else null }
