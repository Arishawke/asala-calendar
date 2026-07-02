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

    // a provisioned pair whose FIRST sync fails (a failed contacts read is a
    // no-op sync) must not read as success: the toggle would claim the feature
    // is on over two silently empty calendars. rollback is flag-only, keeping
    // the created pair and its persisted ids for reuse on the retry, because a
    // teardown here would delete hand-added rows on a transient re-enable failure.
    @Test
    fun `failed first sync rolls back the flag but keeps the provisioned pair`() = runBlocking {
        val calendarOps = FakeOccasionCalendarOps()
        val syncOps = FakeOccasionSyncOps(syncResult = false)
        val provisioner = OccasionProvisioner(calendarOps, prefs, syncOps)

        val ok = provisioner.enable(BIRTHDAYS_NAME, ANNIVERSARIES_NAME, REMINDER_MINUTES, titleFor)

        assertFalse("enable reports failure", ok)
        val after = prefs.prefs.first()
        assertFalse("enabled flag rolled back", after.contactOccasionsEnabled)
        assertEquals("no calendar deleted", emptyList<Long>(), calendarOps.deleteCalls)
        assertEquals("birthdays id kept for the retry", calendarOps.createdIds[0], after.birthdaysCalendarId)
        assertEquals("anniversaries id kept for the retry", calendarOps.createdIds[1], after.anniversariesCalendarId)
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

    // a reinstall wipes the stored ids but not the provider rows: enable must
    // re-adopt the orphaned pair (identified by app-owned occasion rows), not
    // create a fresh pair next to it and duplicate every occasion event.
    @Test
    fun `enable adopts an orphaned provisioned pair instead of creating duplicates`() = runBlocking {
        val calendarOps = FakeOccasionCalendarOps(
            preexisting = listOf(localCal(19L, BIRTHDAYS_NAME), localCal(20L, ANNIVERSARIES_NAME)),
            ownedOccasionIds = mapOf(
                OccasionType.Birthday to setOf(19L),
                OccasionType.Anniversary to setOf(20L),
            ),
        )
        val syncOps = FakeOccasionSyncOps()
        val provisioner = OccasionProvisioner(calendarOps, prefs, syncOps)

        val ok = provisioner.enable(BIRTHDAYS_NAME, ANNIVERSARIES_NAME, REMINDER_MINUTES, titleFor)

        assertTrue("enable reports success", ok)
        assertTrue("no new calendar created", calendarOps.createdIds.isEmpty())
        val after = prefs.prefs.first()
        assertEquals("adopted birthdays id persisted", 19L, after.birthdaysCalendarId)
        assertEquals("adopted anniversaries id persisted", 20L, after.anniversariesCalendarId)
        assertEquals("sync ran against the adopted pair", 19L to 20L, syncOps.syncCalls.single())
    }

    // several reinstall cycles can leave more than one orphan generation; the
    // newest (highest id) is the one the latest install actually populated.
    @Test
    fun `adoption picks the newest orphan generation`() = runBlocking {
        val calendarOps = FakeOccasionCalendarOps(
            preexisting = listOf(
                localCal(14L, BIRTHDAYS_NAME),
                localCal(19L, BIRTHDAYS_NAME),
                localCal(20L, ANNIVERSARIES_NAME),
            ),
            ownedOccasionIds = mapOf(
                OccasionType.Birthday to setOf(14L, 19L),
                OccasionType.Anniversary to setOf(20L),
            ),
        )
        val provisioner = OccasionProvisioner(calendarOps, prefs, FakeOccasionSyncOps())

        provisioner.enable(BIRTHDAYS_NAME, ANNIVERSARIES_NAME, REMINDER_MINUTES, titleFor)

        assertEquals("newest orphan adopted", 19L, prefs.prefs.first().birthdaysCalendarId)
    }

    // a same-named local calendar WITHOUT owned rows may be the user's own
    // (drawer-created calendars share the local account), and disable()
    // deleting an annexed user calendar would be data loss: adoption must key
    // strictly on owned rows and create a fresh calendar otherwise.
    @Test
    fun `adoption never annexes a same-named calendar without owned rows`() = runBlocking {
        val calendarOps = FakeOccasionCalendarOps(
            preexisting = listOf(localCal(19L, BIRTHDAYS_NAME), localCal(20L, ANNIVERSARIES_NAME)),
            ownedOccasionIds = mapOf(OccasionType.Birthday to setOf(19L)),
        )
        val provisioner = OccasionProvisioner(calendarOps, prefs, FakeOccasionSyncOps())

        provisioner.enable(BIRTHDAYS_NAME, ANNIVERSARIES_NAME, REMINDER_MINUTES, titleFor)

        val after = prefs.prefs.first()
        assertEquals("birthdays orphan adopted via owned rows", 19L, after.birthdaysCalendarId)
        assertEquals("anniversaries calendar freshly created", 1, calendarOps.createdIds.size)
        assertEquals(
            "created id persisted, same-named calendar untouched",
            calendarOps.createdIds.single(),
            after.anniversariesCalendarId,
        )
    }

    // an occasion row copied into a synced (non-local) calendar must never make
    // the app adopt that calendar; with no local orphan a fresh pair is created.
    @Test
    fun `adoption ignores occasion rows outside local calendars`() = runBlocking {
        val calendarOps = FakeOccasionCalendarOps(
            preexisting = listOf(syncedCal(99L, BIRTHDAYS_NAME)),
            ownedOccasionIds = mapOf(OccasionType.Birthday to setOf(99L)),
        )
        val provisioner = OccasionProvisioner(calendarOps, prefs, FakeOccasionSyncOps())

        val ok = provisioner.enable(BIRTHDAYS_NAME, ANNIVERSARIES_NAME, REMINDER_MINUTES, titleFor)

        assertTrue("enable reports success", ok)
        assertEquals("both calendars freshly created", 2, calendarOps.createdIds.size)
        assertTrue("synced calendar never adopted", prefs.prefs.first().birthdaysCalendarId != 99L)
    }

    private companion object {
        const val BIRTHDAYS_NAME = "Birthdays"
        const val ANNIVERSARIES_NAME = "Anniversaries"
        const val REMINDER_MINUTES = 30
        const val OWNER_ACCESS = 700

        fun localCal(id: Long, name: String) = CalendarItem(
            id = id,
            displayName = name,
            accountName = "Asala Local",
            accountType = android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL,
            color = 0,
            visible = true,
            accessLevel = OWNER_ACCESS,
        )

        fun syncedCal(id: Long, name: String) = localCal(id, name).copy(accountType = "com.google")
    }
}

// records every create/delete call; createLocalCalendar fails (returns null)
// for any display name in failNames, without mutating the fake's calendar set.
// preexisting + ownedOccasionIds model orphaned provisioned calendars left
// behind by a reinstall, for the adoption tests.
private class FakeOccasionCalendarOps(
    private val failNames: Set<String> = emptySet(),
    preexisting: List<CalendarItem> = emptyList(),
    private val ownedOccasionIds: Map<OccasionType, Set<Long>> = emptyMap(),
) : OccasionCalendarOps {
    val createdIds = mutableListOf<Long>()
    val deleteCalls = mutableListOf<Long>()
    private val items = preexisting.associateBy { it.id }.toMutableMap()
    private var nextId = (preexisting.maxOfOrNull { it.id } ?: 0L) + 1

    override suspend fun ownedOccasionCalendarIds(type: OccasionType): Set<Long> = ownedOccasionIds[type].orEmpty()

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

// records sync() invocations; syncResult=false models a failed contacts read
// (OccasionSync.sync's no-op path) for the flag-only rollback test.
private class FakeOccasionSyncOps(private val syncResult: Boolean = true) : OccasionSyncOps {
    val syncCalls = mutableListOf<Pair<Long, Long>>()

    override suspend fun sync(
        birthdaysCalendarId: Long,
        anniversariesCalendarId: Long,
        reminderMinutes: Int?,
        titleFor: (Occasion) -> String,
    ): Boolean {
        syncCalls += birthdaysCalendarId to anniversariesCalendarId
        return syncResult
    }

    override suspend fun reapplyReminders(
        birthdaysCalendarId: Long,
        anniversariesCalendarId: Long,
        reminderMinutes: Int?,
    ) {
        // not exercised: enable()/ensureAndSync never calls this; only disable() does.
    }
}
