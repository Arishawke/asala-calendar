/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

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

// turns the contact-occasions feature on and off: provisions/tears down the
// two local calendars that own the generated events, and drives the first sync.
class OccasionProvisioner(
    private val calendars: CalendarRepository,
    private val prefs: UserPreferences,
    private val sync: OccasionSync,
) {
    suspend fun enable(
        birthdaysName: String,
        anniversariesName: String,
        reminderMinutes: Int?,
        titleFor: (Occasion) -> String,
    ): Boolean {
        // mark intent before ensureCalendars so its in-lock enabled check passes for
        // the enable path (a background heal reads the same flag and must see it on);
        // roll back if provisioning fails so we don't leave the feature half-enabled.
        prefs.setContactOccasionsEnabled(true)
        val ids = ensureCalendars(birthdaysName, anniversariesName)
        if (ids == null) {
            prefs.setContactOccasionsEnabled(false)
            return false
        }
        sync.sync(ids.birthdays, ids.anniversaries, reminderMinutes, titleFor)
        return true
    }

    // resolves the two occasion calendar ids, re-creating any the user deleted (or
    // that were never provisioned) and persisting the result, so both enable() and
    // a background sync heal instead of writing into a dead id (F5). already-present
    // ids are reused rather than duplicated, which also makes enable() idempotent
    // against a double-tap in the initial-render OFF window (F12). serialized so
    // concurrent callers (enable + contacts observer + daily tick) can't race into
    // a duplicate pair.
    internal suspend fun ensureCalendars(birthdaysName: String, anniversariesName: String): OccasionCalendarIds? =
        provisionMutex.withLock {
            val current = prefs.prefs.first()
            // re-check under the lock: the feature may have been disabled between the
            // caller's gate and here, in which case a background heal must create
            // nothing (disable() also holds this lock, so the two can't interleave).
            if (!current.contactOccasionsEnabled) return@withLock null
            val existing = calendars.calendars().mapTo(HashSet()) { it.id }
            val birthdaysId = resolveOccasionCalendarId(current.birthdaysCalendarId, existing)
                ?: calendars.createLocalCalendar(birthdaysName, BIRTHDAYS_DEFAULT_COLOR)
            val anniversariesId = resolveOccasionCalendarId(current.anniversariesCalendarId, existing)
                ?: calendars.createLocalCalendar(anniversariesName, ANNIVERSARIES_DEFAULT_COLOR)
            if (birthdaysId == null || anniversariesId == null) {
                // a create failed: roll back only a newly-created calendar (leave a
                // reused existing one alone) so no orphan is left behind.
                if (birthdaysId != current.birthdaysCalendarId) birthdaysId?.let { calendars.deleteLocalCalendar(it) }
                if (anniversariesId != current.anniversariesCalendarId) {
                    anniversariesId?.let { calendars.deleteLocalCalendar(it) }
                }
                return@withLock null
            }
            if (birthdaysId != current.birthdaysCalendarId) prefs.setBirthdaysCalendarId(birthdaysId)
            if (anniversariesId != current.anniversariesCalendarId) prefs.setAnniversariesCalendarId(anniversariesId)
            OccasionCalendarIds(birthdaysId, anniversariesId)
        }

    // under provisionMutex so a concurrent background heal can't re-create the pair
    // mid-teardown (it re-reads the now-disabled flag and bails).
    suspend fun disable(birthdaysCalendarId: Long?, anniversariesCalendarId: Long?) = provisionMutex.withLock {
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
