/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.File

// covers OccasionProvisioner's provider-and-prefs orchestration (disable
// teardown, and ensureCalendars' reuse / self-heal / disabled branches) against
// the real CalendarProvider and an isolated DataStore, so the pure
// resolveOccasionCalendarId seam's decisions are proven to actually create,
// reuse, persist, and delete. The DataStore is a throwaway file, so this never
// touches the app's real contact-occasions setting.
@RunWith(AndroidJUnit4::class)
class OccasionProvisionerTeardownTest {
    @get:Rule
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    @get:Rule
    val testName: TestName = TestName()

    private lateinit var context: Context
    private lateinit var cr: ContentResolver
    private lateinit var calendars: CalendarRepository
    private lateinit var prefs: UserPreferences
    private lateinit var provisioner: OccasionProvisioner
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStoreFile: File
    private val createdCalendarIds = mutableListOf<Long>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        cr = context.contentResolver
        calendars = CalendarRepository(cr)
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        // same path preferencesDataStoreFile would produce; a throwaway name per
        // test keeps each run isolated and off the app's real "settings" store.
        dataStoreFile = File(context.filesDir, "datastore/occasion-prov-test-${testName.methodName}.preferences_pb")
        dataStoreFile.parentFile?.mkdirs()
        dataStoreFile.delete()
        prefs = UserPreferences(PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile })
        val sync = OccasionSync(
            contentResolver = cr,
            contacts = ContactsRepository(cr),
            events = EventRepository(cr),
            reminders = RemindersRepository(cr),
            appPackage = context.packageName,
        )
        provisioner = OccasionProvisioner(calendars, prefs, sync)
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        createdCalendarIds.forEach { calendars.deleteLocalCalendar(it) }
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    private suspend fun newCalendar(name: String): Long {
        val id = checkNotNull(calendars.createLocalCalendar(name, CALENDAR_COLOR)) { "calendar create failed" }
        createdCalendarIds += id
        return id
    }

    private suspend fun presentCalendarIds(): Set<Long> = calendars.calendars().mapTo(HashSet()) { it.id }

    @Test
    fun disableDeletesBothCalendarsAndClearsTheStoredIdsAndFlag() = runBlocking {
        val birthdays = newCalendar("Asala Disable B")
        val anniversaries = newCalendar("Asala Disable A")
        prefs.setContactOccasionsEnabled(true)
        prefs.setBirthdaysCalendarId(birthdays)
        prefs.setAnniversariesCalendarId(anniversaries)

        // ids come from the seeded prefs, read inside the provision lock
        provisioner.disable()

        val present = presentCalendarIds()
        assertFalse("birthdays calendar removed", birthdays in present)
        assertFalse("anniversaries calendar removed", anniversaries in present)
        val after = prefs.prefs.first()
        assertNull("birthdays id cleared", after.birthdaysCalendarId)
        assertNull("anniversaries id cleared", after.anniversariesCalendarId)
        assertFalse("feature flag cleared", after.contactOccasionsEnabled)
    }

    @Test
    fun ensureCalendarsReusesStillPresentStoredCalendars() = runBlocking {
        val birthdays = newCalendar("Asala Reuse B")
        val anniversaries = newCalendar("Asala Reuse A")
        prefs.setContactOccasionsEnabled(true)
        prefs.setBirthdaysCalendarId(birthdays)
        prefs.setAnniversariesCalendarId(anniversaries)

        val ids = provisioner.ensureCalendars("Asala Reuse B", "Asala Reuse A")

        // returning the stored ids proves the reuse branch ran (the create `?:`
        // was never taken), so no duplicate pair was provisioned (F12).
        assertNotNull("resolves the pair", ids)
        assertEquals("reuses the stored birthdays id", birthdays, ids?.birthdays)
        assertEquals("reuses the stored anniversaries id", anniversaries, ids?.anniversaries)
    }

    @Test
    fun ensureCalendarsRecreatesACalendarDeletedOutsideTheApp() = runBlocking {
        val anniversaries = newCalendar("Asala Heal A")
        // model a stored birthdays id whose calendar no longer exists (deleted
        // outside the app). a value above every present id can't be a live
        // calendar, and the provider recycles rowids, so a create-then-delete id
        // would be handed straight back on the fresh create and hide the heal.
        val missingBirthdaysId = (presentCalendarIds().maxOrNull() ?: 0L) + MISSING_ID_GAP
        prefs.setContactOccasionsEnabled(true)
        prefs.setBirthdaysCalendarId(missingBirthdaysId)
        prefs.setAnniversariesCalendarId(anniversaries)

        val ids = provisioner.ensureCalendars("Asala Heal B", "Asala Heal A")

        val healed = checkNotNull(ids) { "self-heal must resolve a pair" }.birthdays
        createdCalendarIds += healed
        assertTrue("a fresh birthdays calendar replaces the missing one", healed != missingBirthdaysId)
        assertTrue("the healed calendar exists", healed in presentCalendarIds())
        assertEquals("the surviving anniversaries id is reused", anniversaries, ids.anniversaries)
        assertEquals("the healed id is persisted", healed, prefs.prefs.first().birthdaysCalendarId)
    }

    @Test
    fun ensureCalendarsCreatesNothingWhenTheFeatureIsDisabled() = runBlocking {
        // default store: contactOccasionsEnabled = false. null proves ensureCalendars
        // bailed on the in-lock enabled check before any create.
        val ids = provisioner.ensureCalendars("Asala Off B", "Asala Off A")

        assertNull("disabled feature provisions nothing", ids)
    }

    private companion object {
        const val CALENDAR_COLOR = 0xFF999999.toInt()

        // gap above the largest present calendar id, so a "deleted outside the
        // app" stored id is guaranteed absent and can't collide via rowid reuse.
        const val MISSING_ID_GAP = 1_000_000L
    }
}
