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
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Okabe-Ito palette entries as raw ints; the data layer doesn't depend on ui.theme.
const val BIRTHDAYS_DEFAULT_COLOR: Int = 0xFFE69F00.toInt()
const val ANNIVERSARIES_DEFAULT_COLOR: Int = 0xFF56B4E9.toInt()

// pure: reuse a stored occasion calendar only while it still exists in the
// provider; null signals a re-create (never provisioned, or the user deleted it
// outside the app). backs enable() idempotency (F12) and the deleted-calendar
// self-heal (F5).
fun resolveOccasionCalendarId(storedId: Long?, existingIds: Set<Long>): Long? = storedId?.takeIf { it in existingIds }

// testability seam: the exact CalendarRepository surface OccasionProvisioner
// calls, so its rollback paths are unit-testable behind a fake instead of the
// real CalendarProvider (see OccasionProvisionerRollbackTest).
interface OccasionCalendarOps {
    suspend fun calendars(): List<CalendarItem>

    suspend fun createLocalCalendar(displayName: String, color: Int): Long?

    suspend fun deleteLocalCalendar(calendarId: Long): Boolean

    // ids of calendars holding app-owned occasion rows of the given type; lets
    // provisioning re-adopt an orphaned pair after a reinstall wiped the prefs.
    suspend fun ownedOccasionCalendarIds(type: OccasionType): Set<Long>
}

// testability seam: the exact OccasionSync surface OccasionProvisioner calls.
interface OccasionSyncOps {
    suspend fun sync(
        birthdaysCalendarId: Long,
        anniversariesCalendarId: Long,
        reminderMinutes: Int?,
        titleFor: (Occasion) -> String,
    ): Boolean

    suspend fun reapplyReminders(birthdaysCalendarId: Long, anniversariesCalendarId: Long, reminderMinutes: Int?)
}

// turns the contact-occasions feature on and off: provisions/tears down the
// two local calendars that own the generated events, and drives the first sync.
class OccasionProvisioner(
    private val calendars: OccasionCalendarOps,
    private val prefs: UserPreferences,
    private val sync: OccasionSyncOps,
) {
    suspend fun enable(
        birthdaysName: String,
        anniversariesName: String,
        reminderMinutes: Int?,
        titleFor: (Occasion) -> String,
    ): Boolean {
        // mark intent before ensureAndSync so its in-lock enabled check passes for
        // the enable path (a background heal reads the same flag and must see it on);
        // roll back the flag if provisioning or the first sync fails so the toggle
        // reflects reality. flag-only, not disable(): a full teardown on a transient
        // contacts read failure would delete hand-added rows in an already-populated
        // pair; the persisted ids are reused on the retry instead.
        prefs.setContactOccasionsEnabled(true)
        val ok = ensureAndSync(birthdaysName, anniversariesName, reminderMinutes, titleFor)
        if (!ok) prefs.setContactOccasionsEnabled(false)
        return ok
    }

    // resolves the ids and runs the sync without releasing provisionMutex in
    // between: a disable() interleaving in that gap would delete the calendars
    // and clear the prefs, and the in-flight sync would then write the full
    // event set against dead calendar ids, leaving orphan rows nothing cleans up.
    suspend fun ensureAndSync(
        birthdaysName: String,
        anniversariesName: String,
        reminderMinutes: Int?,
        titleFor: (Occasion) -> String,
    ): Boolean = provisionMutex.withLock {
        val ids = ensureCalendarsLocked(birthdaysName, anniversariesName) ?: return@withLock false
        // propagated, not discarded: a failed contacts read is a no-op sync,
        // and both the enable() rollback and the sync trigger's freshness
        // stamp must not mistake it for a completed reconcile.
        sync.sync(ids.birthdays, ids.anniversaries, reminderMinutes, titleFor)
    }

    // lock-taking wrapper kept for the device tests, which exercise the
    // reuse / heal / disabled-bail branches in isolation.
    internal suspend fun ensureCalendars(birthdaysName: String, anniversariesName: String): OccasionCalendarIds? =
        provisionMutex.withLock { ensureCalendarsLocked(birthdaysName, anniversariesName) }

    // resolves the two occasion calendar ids, re-creating any the user deleted (or
    // that were never provisioned) and persisting the result, so both enable() and
    // a background sync heal instead of writing into a dead id (F5). already-present
    // ids are reused rather than duplicated, which also makes enable() idempotent
    // against a double-tap in the initial-render OFF window (F12). callers hold
    // provisionMutex so concurrent callers (enable + contacts observer + daily
    // tick) can't race into a duplicate pair.
    private suspend fun ensureCalendarsLocked(birthdaysName: String, anniversariesName: String): OccasionCalendarIds? {
        val current = prefs.prefs.first()
        // re-check under the lock: the feature may have been disabled between the
        // caller's gate and here, in which case a background heal must create
        // nothing (disable() also holds this lock, so the two can't interleave).
        if (!current.contactOccasionsEnabled) return null
        val existing = calendars.calendars()
        val existingIds = existing.mapTo(HashSet()) { it.id }
        // reuse the stored id, else re-adopt an orphaned provisioned calendar (a
        // reinstall wipes the prefs but not the provider rows, and creating a
        // fresh pair next to the orphan would duplicate every occasion event),
        // else create a new one.
        val reusedBirthdays = resolveOccasionCalendarId(current.birthdaysCalendarId, existingIds)
            ?: adoptOrphanedCalendar(OccasionType.Birthday, existing)
        val createdBirthdays =
            if (reusedBirthdays == null) calendars.createLocalCalendar(birthdaysName, BIRTHDAYS_DEFAULT_COLOR) else null
        val reusedAnniversaries = resolveOccasionCalendarId(current.anniversariesCalendarId, existingIds)
            ?: adoptOrphanedCalendar(OccasionType.Anniversary, existing)
        val createdAnniversaries = if (reusedAnniversaries == null) {
            calendars.createLocalCalendar(anniversariesName, ANNIVERSARIES_DEFAULT_COLOR)
        } else {
            null
        }
        val birthdaysId = reusedBirthdays ?: createdBirthdays
        val anniversariesId = reusedAnniversaries ?: createdAnniversaries
        return if (birthdaysId == null || anniversariesId == null) {
            // a create failed: roll back only a freshly-created calendar (never
            // a reused or adopted one, which holds real events) so no orphan is
            // left behind.
            createdBirthdays?.let { calendars.deleteLocalCalendar(it) }
            createdAnniversaries?.let { calendars.deleteLocalCalendar(it) }
            null
        } else {
            if (birthdaysId != current.birthdaysCalendarId) prefs.setBirthdaysCalendarId(birthdaysId)
            if (anniversariesId != current.anniversariesCalendarId) prefs.setAnniversariesCalendarId(anniversariesId)
            OccasionCalendarIds(birthdaysId, anniversariesId)
        }
    }

    // newest orphan generation wins when several reinstall cycles left more
    // than one behind; only LOCAL calendars qualify (an occasion row copied
    // into a synced calendar must never make the app adopt it). adoption keys
    // strictly on owned rows: an EMPTY orphan is deliberately NOT re-adopted
    // by name, because a same-named empty calendar can be the user's own, and
    // disable() deleting an annexed user calendar would be data loss. worst
    // case is one harmless empty duplicate after a reinstall.
    private suspend fun adoptOrphanedCalendar(type: OccasionType, existing: List<CalendarItem>): Long? {
        val localIds = existing
            .filter { it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL }
            .mapTo(HashSet()) { it.id }
        return calendars.ownedOccasionCalendarIds(type).filter { it in localIds }.maxOrNull()
    }

    // under provisionMutex so a concurrent background heal can't re-create the pair
    // mid-teardown (it re-reads the now-disabled flag and bails). the ids are read
    // from prefs inside the lock: a caller-captured snapshot can be stale when a
    // heal re-provisioned in flight, and deleting the stale ids would orphan the
    // freshly healed, populated pair.
    suspend fun disable() = provisionMutex.withLock {
        val current = prefs.prefs.first()
        val birthdaysCalendarId = current.birthdaysCalendarId
        val anniversariesCalendarId = current.anniversariesCalendarId
        if (birthdaysCalendarId != null && anniversariesCalendarId != null) {
            // drop reminder rows first so no orphan alarm survives the calendar delete
            sync.reapplyReminders(birthdaysCalendarId, anniversariesCalendarId, reminderMinutes = null)
        }
        birthdaysCalendarId?.let { calendars.deleteLocalCalendar(it) }
        anniversariesCalendarId?.let { calendars.deleteLocalCalendar(it) }

        prefs.setBirthdaysCalendarId(null)
        prefs.setAnniversariesCalendarId(null)
        prefs.setContactOccasionsEnabled(false)
    }

    private companion object {
        // process-wide, like OccasionSync.syncMutex: every syncOccasionsIfEnabled
        // builds a fresh OccasionProvisioner, so a shared lock is what actually
        // serializes concurrent provisioning.
        val provisionMutex = Mutex()
    }
}
