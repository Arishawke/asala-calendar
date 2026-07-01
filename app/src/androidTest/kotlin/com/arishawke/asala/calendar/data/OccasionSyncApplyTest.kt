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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// exercises OccasionSync's provider write wiring (the piece the pure
// OccasionReconcile / planOccasions seams cannot cover) against the real
// CalendarProvider, using throwaway LOCAL calendars deleted in tearDown. Drives
// applyDiff with a hand-built diff so the insert/update/delete paths are
// deterministic without the non-deterministic contacts read.
@RunWith(AndroidJUnit4::class)
class OccasionSyncApplyTest {
    @get:Rule
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private lateinit var context: Context
    private lateinit var cr: ContentResolver
    private lateinit var calendars: CalendarRepository
    private lateinit var events: EventRepository
    private lateinit var sync: OccasionSync
    private val createdCalendarIds = mutableListOf<Long>()

    private val titleFor: (Occasion) -> String = { "${it.displayName} ${it.type.name}" }

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        cr = context.contentResolver
        calendars = CalendarRepository(cr)
        events = EventRepository(cr)
        sync = OccasionSync(
            contentResolver = cr,
            contacts = ContactsRepository(cr),
            events = events,
            reminders = RemindersRepository(cr),
            appPackage = context.packageName,
        )
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        createdCalendarIds.forEach { calendars.deleteLocalCalendar(it) }
    }

    private suspend fun newCalendar(name: String): Long {
        val id = checkNotNull(calendars.createLocalCalendar(name, CALENDAR_COLOR)) { "calendar create failed" }
        createdCalendarIds += id
        return id
    }

    private suspend fun insertOccasion(occasion: Occasion, calendarId: Long): Long = checkNotNull(
        events.insertEvent(
            occasionEventDraft(occasion, calendarId, context.packageName, titleFor(occasion), occasion.displayName),
        ),
    ) { "occasion insert failed" }

    @Test
    fun applyDiffInsertsEachOccasionWithItsReminder() = runBlocking {
        val calId = newCalendar("Asala Apply Insert")

        sync.applyDiff(OccasionDiff(listOf(ALICE, BOB), emptyList(), emptyList()), calId, titleFor, REMINDER_MIN)

        val ids = cr.occasionEventIdsIn(calId)
        assertEquals("both occasions inserted", 2, ids.size)
        for (id in ids) {
            assertEquals("insert wires up the reminder", listOf(REMINDER_MIN), cr.reminderMinutesFor(id))
        }
    }

    @Test
    fun applyDiffUpdatesChangedTitleAndSetsReminder() = runBlocking {
        val calId = newCalendar("Asala Apply Update")
        val eventId = insertOccasion(ALICE, calId)
        val renamed = ALICE.copy(displayName = "Alicia")

        sync.applyDiff(
            OccasionDiff(emptyList(), listOf(eventId to renamed), emptyList()),
            calId,
            titleFor,
            REMINDER_MIN,
        )

        assertEquals("same row updated in place", listOf(eventId), cr.occasionEventIdsIn(calId))
        assertEquals("title reflects the new name", titleFor(renamed), cr.titleOf(eventId))
        assertEquals("update re-sets the reminder", listOf(REMINDER_MIN), cr.reminderMinutesFor(eventId))
    }

    @Test
    fun applyDiffDeletesRemovedOccasion() = runBlocking {
        val calId = newCalendar("Asala Apply Delete")
        val eventId = insertOccasion(ALICE, calId)

        sync.applyDiff(OccasionDiff(emptyList(), emptyList(), listOf(eventId)), calId, titleFor, REMINDER_MIN)

        assertTrue("the whole occasion series is gone", cr.occasionEventIdsIn(calId).isEmpty())
    }

    @Test
    fun reapplyRemindersSetsReminderOnEveryOccasionEvent() = runBlocking {
        val birthdays = newCalendar("Asala Reapply B")
        val anniversaries = newCalendar("Asala Reapply A")
        val birthdayId = insertOccasion(ALICE, birthdays)
        val anniversaryId = insertOccasion(BOB, anniversaries)

        sync.reapplyReminders(birthdays, anniversaries, REMINDER_MIN)

        assertEquals(listOf(REMINDER_MIN), cr.reminderMinutesFor(birthdayId))
        assertEquals(listOf(REMINDER_MIN), cr.reminderMinutesFor(anniversaryId))
    }

    @Test
    fun reapplyRemindersWithNullClearsReminders() = runBlocking {
        val birthdays = newCalendar("Asala Clear B")
        val anniversaries = newCalendar("Asala Clear A")
        val birthdayId = insertOccasion(ALICE, birthdays)
        sync.reapplyReminders(birthdays, anniversaries, REMINDER_MIN)
        assertEquals("reminder present before the clear", listOf(REMINDER_MIN), cr.reminderMinutesFor(birthdayId))

        sync.reapplyReminders(birthdays, anniversaries, null)

        assertTrue("null offset drops the reminder row", cr.reminderMinutesFor(birthdayId).isEmpty())
    }

    private companion object {
        const val CALENDAR_COLOR = 0xFF999999.toInt()
        const val REMINDER_MIN = 30
        val ALICE = Occasion(1001, "Alice", OccasionType.Birthday, 6, 15, 1990)
        val BOB = Occasion(1002, "Bob", OccasionType.Anniversary, 3, 4, 2005)
    }
}
