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
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Exercises the scoped delete/edit against the real CalendarProvider on-device.
// These paths fail only at the provider's instance-expansion layer, which JVM
// unit tests cannot model, so the map-assembly tests pass while the series
// silently vanishes. Each test uses a throwaway LOCAL calendar (deleted in
// tearDown) and a far-future UTC anchor so occurrences are exactly one day
// apart and never collide with real events.
@RunWith(AndroidJUnit4::class)
class RecurringSingleOccurrenceTest {
    @get:Rule
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val account = "AsalaInstrTest"
    private val anchor = 2_050_000_000_000L
    private val dayMs = 86_400_000L
    private val windowStart = anchor - dayMs
    private val windowEnd = anchor + 6 * dayMs

    // all-day occurrences sit at UTC midnight; floor the anchor to a day boundary.
    private val allDayAnchor = anchor / dayMs * dayMs

    private lateinit var cr: ContentResolver
    private var calendarId = -1L

    @Before
    fun setUp() {
        cr = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        calendarId = createLocalCalendar()
    }

    @After
    fun tearDown() {
        if (calendarId > 0) {
            cr.delete(calendarSyncUri(), "${CalendarContract.Calendars._ID} = ?", arrayOf(calendarId.toString()))
        }
    }

    @Test
    fun deleteThisInstance_removesOnlyThatOccurrence() = runBlocking {
        val parentId = insertDailySeries(count = 5)
        assertEquals("baseline: series expands to 5 occurrences", 5, visibleBegins().size)

        val target = anchor + dayMs // occurrence #2
        val ok =
            cr.deleteEventScoped(
                eventId = parentId,
                scope = RecurringEditScope.ThisInstance,
                instanceMillis = target,
                parentAllDay = false,
            )
        assertTrue("provider write reported success", ok)

        val begins = visibleBegins().sorted()
        assertEquals(
            "only the deleted occurrence is gone; the other four survive",
            listOf(anchor, anchor + 2 * dayMs, anchor + 3 * dayMs, anchor + 4 * dayMs),
            begins,
        )
        assertFalse("the deleted occurrence is gone", target in begins)
    }

    @Test
    fun editThisInstance_changesOnlyThatOccurrence() = runBlocking {
        val parentId = insertDailySeries(count = 5)
        assertEquals("baseline: series expands to 5 occurrences", 5, visibleBegins().size)

        val target = anchor + dayMs // occurrence #2
        val movedStart = target + 3 * 3_600_000L // shift +3h
        val draft =
            EventDraft(
                calendarId = calendarId,
                title = "Edited",
                description = null,
                location = null,
                startMillis = movedStart,
                endMillis = movedStart + 3_600_000L,
                allDay = false,
                eventTimezone = "UTC",
                rrule = null,
            )
        val rowId =
            cr.updateEventScoped(
                eventId = parentId,
                draft = draft,
                scope = RecurringEditScope.ThisInstance,
                instanceMillis = target,
                parentAllDay = false,
            )
        assertTrue("edit reported success", rowId != null && rowId > 0)

        val begins = visibleBegins().sorted()
        assertEquals(
            "the series survives: four originals plus the moved occurrence",
            listOf(anchor, movedStart, anchor + 2 * dayMs, anchor + 3 * dayMs, anchor + 4 * dayMs).sorted(),
            begins,
        )
        assertTrue("the edited occurrence carries the new title", "Edited" in titlesAt(movedStart))
    }

    @Test
    fun deleteFirstOccurrence_removesOnlyTheFirst() = runBlocking {
        val parentId = insertDailySeries(count = 5)
        assertEquals(5, visibleBegins().size)

        val ok =
            cr.deleteEventScoped(
                eventId = parentId,
                scope = RecurringEditScope.ThisInstance,
                instanceMillis = anchor, // occurrence #1
                parentAllDay = false,
            )
        assertTrue(ok)
        assertEquals(
            "deleting the first occurrence leaves the rest of the series",
            listOf(anchor + dayMs, anchor + 2 * dayMs, anchor + 3 * dayMs, anchor + 4 * dayMs),
            visibleBegins().sorted(),
        )
    }

    @Test
    fun deleteTwoOccurrences_excludesBothAndKeepsTheRest() = runBlocking {
        val parentId = insertDailySeries(count = 5)
        cr.deleteEventScoped(parentId, RecurringEditScope.ThisInstance, anchor + dayMs, parentAllDay = false)
        cr.deleteEventScoped(parentId, RecurringEditScope.ThisInstance, anchor + 3 * dayMs, parentAllDay = false)
        assertEquals(
            "a second deletion accumulates on EXDATE without resurrecting the first",
            listOf(anchor, anchor + 2 * dayMs, anchor + 4 * dayMs),
            visibleBegins().sorted(),
        )
    }

    @Test
    fun deleteAllDayOccurrence_removesOnlyThatDay() = runBlocking {
        val parentId = insertAllDayDailySeries(count = 5)
        assertEquals("baseline: all-day series expands to 5 days", 5, visibleBegins().size)

        val ok =
            cr.deleteEventScoped(
                eventId = parentId,
                scope = RecurringEditScope.ThisInstance,
                instanceMillis = allDayAnchor + dayMs, // day #2 at UTC midnight
                parentAllDay = true,
            )
        assertTrue(ok)
        assertEquals(
            "only the deleted all-day occurrence is gone; the other four survive",
            listOf(
                allDayAnchor,
                allDayAnchor + 2 * dayMs,
                allDayAnchor + 3 * dayMs,
                allDayAnchor + 4 * dayMs,
            ),
            visibleBegins().sorted(),
        )
    }

    // visible occurrences in the window for the test calendar, excluding any the
    // provider marks STATUS_CANCELED (those must not reach the calendar views).
    private fun visibleBegins(): List<Long> = readInstances().filter { it.status.isVisible() }.map { it.begin }

    private fun titlesAt(begin: Long): List<String> =
        readInstances().filter { it.begin == begin && it.status.isVisible() }.map { it.title }

    private fun Int?.isVisible(): Boolean = this != CalendarContract.Events.STATUS_CANCELED

    private data class Row(val begin: Long, val title: String, val status: Int?)

    private fun readInstances(): List<Row> {
        val rows = mutableListOf<Row>()
        cr.query(
            instancesUriFor(windowStart, windowEnd),
            arrayOf(
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.STATUS,
            ),
            "${CalendarContract.Instances.CALENDAR_ID} = ?",
            arrayOf(calendarId.toString()),
            null,
        )?.use { c ->
            val begin = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val title = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val status = c.getColumnIndexOrThrow(CalendarContract.Instances.STATUS)
            while (c.moveToNext()) {
                rows.add(
                    Row(
                        begin = c.getLong(begin),
                        title = c.getString(title).orEmpty(),
                        status = if (c.isNull(status)) null else c.getInt(status),
                    ),
                )
            }
        }
        return rows
    }

    private fun insertDailySeries(count: Int): Long {
        val values =
            ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, "Series")
                put(CalendarContract.Events.DTSTART, anchor)
                put(CalendarContract.Events.DURATION, "PT1H")
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                put(CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=$count")
            }
        val uri = cr.insert(CalendarContract.Events.CONTENT_URI, values) ?: error("event insert failed")
        return ContentUris.parseId(uri)
    }

    private fun insertAllDayDailySeries(count: Int): Long {
        val values =
            ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, "AllDaySeries")
                put(CalendarContract.Events.DTSTART, allDayAnchor)
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.DURATION, "P1D")
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                put(CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=$count")
            }
        val uri = cr.insert(CalendarContract.Events.CONTENT_URI, values) ?: error("all-day event insert failed")
        return ContentUris.parseId(uri)
    }

    private fun createLocalCalendar(): Long {
        // self-heal a throwaway calendar orphaned by a killed prior run
        cr.delete(calendarSyncUri(), "${CalendarContract.Calendars.ACCOUNT_NAME} = ?", arrayOf(account))
        val values =
            ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, account)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, account)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, account)
                put(CalendarContract.Calendars.CALENDAR_COLOR, -0xff0100)
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            }
        val uri = cr.insert(calendarSyncUri(), values) ?: error("calendar insert failed")
        return ContentUris.parseId(uri)
    }

    private fun calendarSyncUri(): Uri = CalendarContract.Calendars.CONTENT_URI
        .buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()
}
