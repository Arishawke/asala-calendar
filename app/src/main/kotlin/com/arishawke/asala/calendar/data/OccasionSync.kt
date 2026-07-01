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
import android.database.Cursor
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OccasionApplyPlan(val birthdays: OccasionDiff, val anniversaries: OccasionDiff)

// pure planner: no provider access, so the guard (a failed contacts read must
// not be mistaken for a genuinely empty address book and wipe every existing
// occasion event) is provable without a ContentResolver. see OccasionSyncGuardTest.
fun planOccasions(
    read: OccasionReadResult,
    existingBirthdays: List<ExistingOccasionEvent>,
    existingAnniversaries: List<ExistingOccasionEvent>,
    titleFor: (Occasion) -> String,
): OccasionApplyPlan? {
    val occasions =
        when (read) {
            is OccasionReadResult.Failed -> return null
            is OccasionReadResult.Success -> read.occasions
        }
    val (birthdays, anniversaries) = occasions.partition { it.type == OccasionType.Birthday }
    return OccasionApplyPlan(
        birthdays = OccasionReconcile.diff(birthdays, existingBirthdays, titleFor),
        anniversaries = OccasionReconcile.diff(anniversaries, existingAnniversaries, titleFor),
    )
}

// orchestrates contact occasions into the two provisioned calendars: reads
// contacts and the previously generated events, diffs via OccasionReconcile,
// and applies the delta. these events are app-owned and never split into
// per-instance edits, so every write is whole-series (RecurringEditScope
// defaults to AllEvents on EventRepository).
class OccasionSync(
    private val contentResolver: ContentResolver,
    private val contacts: ContactsRepository,
    private val events: EventRepository,
    private val reminders: RemindersRepository,
    private val appPackage: String,
) {
    suspend fun sync(
        birthdaysCalendarId: Long,
        anniversariesCalendarId: Long,
        reminderMinutes: Int?,
        titleFor: (Occasion) -> String,
    ): Boolean {
        val plan =
            planOccasions(
                contacts.readOccasions(),
                readExisting(birthdaysCalendarId),
                readExisting(anniversariesCalendarId),
                titleFor,
            ) ?: return false

        applyDiff(plan.birthdays, birthdaysCalendarId, titleFor, reminderMinutes)
        applyDiff(plan.anniversaries, anniversariesCalendarId, titleFor, reminderMinutes)
        return true
    }

    suspend fun reapplyReminders(birthdaysCalendarId: Long, anniversariesCalendarId: Long, reminderMinutes: Int?) {
        val existing = readExisting(birthdaysCalendarId) + readExisting(anniversariesCalendarId)
        for (event in existing) reminders.setReminder(event.eventId, reminderMinutes)
    }

    private suspend fun applyDiff(
        diff: OccasionDiff,
        calendarId: Long,
        titleFor: (Occasion) -> String,
        reminderMinutes: Int?,
    ) {
        for (occasion in diff.toInsert) {
            val draft = occasion.toDraft(calendarId, titleFor)
            events.insertEvent(draft)?.let { reminders.setReminder(it, reminderMinutes) }
        }
        for ((eventId, occasion) in diff.toUpdate) {
            val draft = occasion.toDraft(calendarId, titleFor)
            events.updateEvent(eventId, draft)?.let { reminders.setReminder(it, reminderMinutes) }
        }
        for (eventId in diff.toDelete) events.deleteEvent(eventId)
    }

    private fun Occasion.toDraft(calendarId: Long, titleFor: (Occasion) -> String): EventDraft =
        occasionEventDraft(this, calendarId, appPackage, titleFor(this), displayName)

    // app-owned occasion events carry a parseable CUSTOM_APP_URI (Task 3); rows
    // without one are hand-added by the user in this calendar and left alone.
    private suspend fun readExisting(calendarId: Long): List<ExistingOccasionEvent> = withContext(Dispatchers.IO) {
        providerCall("readOccasionEvents", onError = emptyList()) {
            val cursor =
                contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    Projection,
                    "${CalendarContract.Events.CALENDAR_ID} = ?",
                    arrayOf(calendarId.toString()),
                    null,
                ) ?: return@providerCall emptyList()

            cursor.use { it.readExistingOccasionEvents() }
        }
    }

    private fun Cursor.readExistingOccasionEvents(): List<ExistingOccasionEvent> {
        val idIdx = getColumnIndexOrThrow(CalendarContract.Events._ID)
        val titleIdx = getColumnIndexOrThrow(CalendarContract.Events.TITLE)
        val startIdx = getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
        val uriIdx = getColumnIndexOrThrow(CalendarContract.Events.CUSTOM_APP_URI)
        val rows = mutableListOf<ExistingOccasionEvent>()
        while (moveToNext()) {
            val (contactId, type) = parseOccasionUri(getString(uriIdx)) ?: continue
            rows +=
                ExistingOccasionEvent(
                    eventId = getLong(idIdx),
                    stableId = "$contactId:${type.name}",
                    title = getString(titleIdx) ?: "",
                    dtStartMillis = getLong(startIdx),
                )
        }
        return rows
    }

    private companion object {
        val Projection =
            arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.CUSTOM_APP_URI,
            )
    }
}
