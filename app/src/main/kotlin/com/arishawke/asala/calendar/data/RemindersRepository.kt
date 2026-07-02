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
     * Replaces all reminders for the event with one row per distinct value. False
     * means the provider rejected an insert (permission revoked / account removed
     * mid-save); the caller should surface that, not assume the reminders are set.
     */
    suspend fun setReminders(eventId: Long, minutes: List<Int>): Boolean = withContext(Dispatchers.IO) {
        providerCall("setReminders", onError = false) {
            contentResolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
            )
            var allInserted = true
            for (m in minutes.distinct()) {
                val cv =
                    ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, eventId)
                        put(CalendarContract.Reminders.MINUTES, m)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    }
                if (contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, cv) == null) {
                    Timber.w("setReminders: provider rejected reminder insert for event %d minutes %d", eventId, m)
                    allInserted = false
                }
            }
            allInserted
        }
    }
}
