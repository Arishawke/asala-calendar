/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

// enable()'s rollback contract, behind fakes for the two provider seams
// (OccasionCalendarOps / OccasionSyncOps) plus a real UserPreferences over a
// throwaway DataStore file, so the prefs assertions exercise the actual
// read/write path rather than a stub. Mirrors the source comment on
// ensureCalendarsLocked: "roll back only a newly-created calendar (leave a
// reused existing one alone) so no orphan is left behind."
class OccasionProvisionerRollbackTest {
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStoreFile: File
    private lateinit var prefs: UserPreferences

    private val titleFor: (Occasion) -> String = { it.displayName }

    @Before
    fun setUp() {
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStoreFile = File.createTempFile("occasion-prov-rollback", ".preferences_pb")
        prefs = UserPreferences(PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile })
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    // failing the FIRST create must not leave the feature half-on: enable()
    // reports failure, the optimistic flag flip is undone, and no sync runs
    // against calendar ids that were never actually provisioned.
    @Test
    fun `birthdays create failure rolls back the enabled flag and runs no sync`() = runBlocking {
        val calendarOps = FakeOccasionCalendarOps(failNames = setOf(BIRTHDAYS_NAME))
        val syncOps = FakeOccasionSyncOps()
        val provisioner = OccasionProvisioner(calendarOps, prefs, syncOps)

        val ok = provisioner.enable(BIRTHDAYS_NAME, ANNIVERSARIES_NAME, REMINDER_MINUTES, titleFor)

        assertFalse("enable reports failure", ok)
        assertFalse("enabled flag rolled back", prefs.prefs.first().contactOccasionsEnabled)
        assertTrue("no sync ran against unprovisioned ids", syncOps.syncCalls.isEmpty())
    }

    // failing the SECOND create must roll back only the half it actually
    // created: the freshly created birthdays calendar is deleted, and prefs end
    // up with neither id stored, not a half-provisioned pair.
    @Test
    fun `anniversaries create failure deletes only the freshly created birthdays calendar`() = runBlocking {
        val calendarOps = FakeOccasionCalendarOps(failNames = setOf(ANNIVERSARIES_NAME))
        val syncOps = FakeOccasionSyncOps()
        val provisioner = OccasionProvisioner(calendarOps, prefs, syncOps)

        val ok = provisioner.enable(BIRTHDAYS_NAME, ANNIVERSARIES_NAME, REMINDER_MINUTES, titleFor)

        assertFalse("enable reports failure", ok)
        assertEquals("only the birthdays create ever succeeds", 1, calendarOps.createdIds.size)
        assertEquals(
            "the newly created birthdays calendar is deleted, nothing else",
            calendarOps.createdIds,
            calendarOps.deleteCalls,
        )
        val after = prefs.prefs.first()
        assertNull("no birthdays id persisted", after.birthdaysCalendarId)
        assertNull("no anniversaries id persisted", after.anniversariesCalendarId)
    }

    // both creates succeeding is the only path that persists ids and runs a
    // sync; the sync must fire exactly once, with the ids just created.
    @Test
    fun `happy path persists both ids and syncs exactly once`() = runBlocking {
        val calendarOps = FakeOccasionCalendarOps()
        val syncOps = FakeOccasionSyncOps()
        val provisioner = OccasionProvisioner(calendarOps, prefs, syncOps)

        val ok = provisioner.enable(BIRTHDAYS_NAME, ANNIVERSARIES_NAME, REMINDER_MINUTES, titleFor)

        assertTrue("enable reports success", ok)
        val (birthdaysId, anniversariesId) = calendarOps.createdIds
        val after = prefs.prefs.first()
        assertEquals("birthdays id persisted", birthdaysId, after.birthdaysCalendarId)
        assertEquals("anniversaries id persisted", anniversariesId, after.anniversariesCalendarId)
        assertTrue("enabled flag stays on", after.contactOccasionsEnabled)
        assertEquals("sync ran exactly once", 1, syncOps.syncCalls.size)
        assertEquals(birthdaysId to anniversariesId, syncOps.syncCalls.single())
    }

    private companion object {
        const val BIRTHDAYS_NAME = "Birthdays"
        const val ANNIVERSARIES_NAME = "Anniversaries"
        const val REMINDER_MINUTES = 30
    }
}

// records every create/delete call; createLocalCalendar fails (returns null)
// for any display name in failNames, without mutating the fake's calendar set.
private class FakeOccasionCalendarOps(private val failNames: Set<String> = emptySet()) : OccasionCalendarOps {
    val createdIds = mutableListOf<Long>()
    val deleteCalls = mutableListOf<Long>()
    private val items = mutableMapOf<Long, CalendarItem>()
    private var nextId = 1L

    override suspend fun calendars(): List<CalendarItem> = items.values.toList()

    override suspend fun createLocalCalendar(displayName: String, color: Int): Long? {
        if (displayName in failNames) return null
        val id = nextId++
        items[id] = CalendarItem(
            id = id,
            displayName = displayName,
            accountName = "local",
            accountType = "LOCAL",
            color = color,
            visible = true,
            accessLevel = OWNER_ACCESS_LEVEL,
        )
        createdIds += id
        return id
    }

    override suspend fun deleteLocalCalendar(calendarId: Long): Boolean {
        deleteCalls += calendarId
        return items.remove(calendarId) != null
    }

    private companion object {
        const val OWNER_ACCESS_LEVEL = 700
    }
}

// records sync() invocations; always reports success since these tests exercise
// the provisioning rollback, not sync's own failure modes.
private class FakeOccasionSyncOps : OccasionSyncOps {
    val syncCalls = mutableListOf<Pair<Long, Long>>()

    override suspend fun sync(
        birthdaysCalendarId: Long,
        anniversariesCalendarId: Long,
        reminderMinutes: Int?,
        titleFor: (Occasion) -> String,
    ): Boolean {
        syncCalls += birthdaysCalendarId to anniversariesCalendarId
        return true
    }

    override suspend fun reapplyReminders(
        birthdaysCalendarId: Long,
        anniversariesCalendarId: Long,
        reminderMinutes: Int?,
    ) {
        // not exercised: enable()/ensureAndSync never calls this; only disable() does.
    }
}
