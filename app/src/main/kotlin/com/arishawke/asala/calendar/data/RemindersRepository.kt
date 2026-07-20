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
import android.content.ContentValues
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class RemindersRepository(private val contentResolver: ContentResolver) {
    /**
     * Replaces all reminders for the event. Editable offsets are written as
     * METHOD_ALERT; preserved rows (the -1 synced default sentinel and any
     * server-owned METHOD_EMAIL/SMS/ALARM row) are written back with their own
     * method so a synced calendar's reminder is not clobbered. Dedupes by the full
     * (minutes, method) row. False means the provider rejected an insert (permission
     * revoked / account removed mid-save); the caller should surface that, not
     * assume the reminders are set.
     */
    suspend fun setReminders(eventId: Long, editable: List<Int>, preserved: List<ReminderRow> = emptyList()): Boolean =
        withContext(Dispatchers.IO) {
            providerCall("setReminders", onError = false) {
                contentResolver.delete(
                    CalendarContract.Reminders.CONTENT_URI,
                    "${CalendarContract.Reminders.EVENT_ID} = ?",
                    arrayOf(eventId.toString()),
                )
                val rows = buildReminderRows(editable, preserved)
                var allInserted = true
                for (row in rows) {
                    val cv =
                        ContentValues().apply {
                            put(CalendarContract.Reminders.EVENT_ID, eventId)
                            put(CalendarContract.Reminders.MINUTES, row.minutes)
                            put(CalendarContract.Reminders.METHOD, row.method)
                        }
                    if (contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, cv) == null) {
                        Timber.w(
                            "setReminders: provider rejected reminder insert for event %d minutes %d",
                            eventId,
                            row.minutes,
                        )
                        allInserted = false
                    }
                }
                allInserted
            }
        }
}

// editable offsets become METHOD_ALERT, preserved rows keep their method, deduped
// by the full (minutes, method) row so a same-minute email and alert both survive.
internal fun buildReminderRows(editable: List<Int>, preserved: List<ReminderRow>): List<ReminderRow> =
    (editable.map { ReminderRow(it, CalendarContract.Reminders.METHOD_ALERT) } + preserved).distinct()
