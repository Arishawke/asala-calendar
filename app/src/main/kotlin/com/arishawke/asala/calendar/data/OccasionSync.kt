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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class OccasionApplyPlan(val birthdays: OccasionDiff, val anniversaries: OccasionDiff)

// pure planner: no provider access, so the guard (a failed contacts read must
// not be mistaken for a genuinely empty address book and wipe every existing
// occasion event) is provable without a ContentResolver. see OccasionSyncGuardTest.
// a null existing-events list means that calendar's read failed too: skip it
// (empty diff) rather than treat "failed" as "empty", which would queue every
// desired occasion as a spurious insert.
fun planOccasions(
    read: OccasionReadResult,
    existingBirthdays: List<ExistingOccasionEvent>?,
    existingAnniversaries: List<ExistingOccasionEvent>?,
    titleFor: (Occasion) -> String,
): OccasionApplyPlan? {
    val occasions =
        when (read) {
            is OccasionReadResult.Failed -> return null
            is OccasionReadResult.Success -> read.occasions
        }
    val (birthdays, anniversaries) = occasions.partition { it.type == OccasionType.Birthday }
    return OccasionApplyPlan(
        birthdays = diffOrSkip(birthdays, existingBirthdays, titleFor),
        anniversaries = diffOrSkip(anniversaries, existingAnniversaries, titleFor),
    )
}

private fun diffOrSkip(
    desired: List<Occasion>,
    existing: List<ExistingOccasionEvent>?,
    titleFor: (Occasion) -> String,
): OccasionDiff = if (existing == null) {
    OccasionDiff(emptyList(), emptyList(), emptyList())
} else {
    OccasionReconcile.diff(desired, existing, titleFor)
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
    // syncMutex is companion-scoped (one lock for every instance), since each
    // syncOccasionsIfEnabled call builds a fresh OccasionSync; without a shared
    // lock, two interleaved syncs can both read-before-either-writes and insert
    // the same occasion twice.
    suspend fun sync(
        birthdaysCalendarId: Long,
        anniversariesCalendarId: Long,
        reminderMinutes: Int?,
        titleFor: (Occasion) -> String,
    ): Boolean = syncMutex.withLock {
        val plan =
            planOccasions(
                contacts.readOccasions(),
                readExisting(birthdaysCalendarId),
                readExisting(anniversariesCalendarId),
                titleFor,
            ) ?: return@withLock false

        applyDiff(plan.birthdays, birthdaysCalendarId, titleFor, reminderMinutes)
        applyDiff(plan.anniversaries, anniversariesCalendarId, titleFor, reminderMinutes)
        true
    }

    suspend fun reapplyReminders(birthdaysCalendarId: Long, anniversariesCalendarId: Long, reminderMinutes: Int?) =
        syncMutex.withLock {
            // a failed read has nothing to reapply reminders to; skip that calendar this cycle
            val existing =
                readExisting(birthdaysCalendarId).orEmpty() + readExisting(anniversariesCalendarId).orEmpty()
            for (event in existing) reminders.setReminder(event.eventId, reminderMinutes)
        }

    // internal (not private) so the androidTest can drive the insert/update/delete
    // write wiring with a hand-built diff, without the non-deterministic contacts read.
    internal suspend fun applyDiff(
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
            events.updateEvent(eventId, draft, scope = RecurringEditScope.AllEvents)
                ?.let { reminders.setReminder(it, reminderMinutes) }
        }
        for (eventId in diff.toDelete) events.deleteEvent(eventId, scope = RecurringEditScope.AllEvents)
    }

    private fun Occasion.toDraft(calendarId: Long, titleFor: (Occasion) -> String): EventDraft =
        occasionEventDraft(this, calendarId, appPackage, titleFor(this), displayName)

    // app-owned occasion events carry a parseable CUSTOM_APP_URI (Task 3); rows
    // without one are hand-added by the user in this calendar and left alone.
    // null means the read failed (distinct from a genuinely empty calendar);
    // callers must not treat that as "delete/replace everything".
    private suspend fun readExisting(calendarId: Long): List<ExistingOccasionEvent>? = withContext(Dispatchers.IO) {
        providerCall("readOccasionEvents", onError = null) {
            val cursor =
                contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    Projection,
                    "${CalendarContract.Events.CALENDAR_ID} = ?",
                    arrayOf(calendarId.toString()),
                    null,
                ) ?: return@providerCall null

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
        // process-wide: shared across every OccasionSync instance so concurrent
        // callers (foreground, daily re-arm, contacts observer, enable-time sync)
        // serialize instead of racing each other's read-then-write.
        val syncMutex = Mutex()
        val Projection =
            arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.CUSTOM_APP_URI,
            )
    }
}
