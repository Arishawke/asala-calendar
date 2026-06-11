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
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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

    @Test
    fun deleteThisInstance_onNonUtcSeries_excludesOnlyThatOccurrence() = runBlocking {
        // a daily series in a non-UTC zone spanning a DST transition: the UTC
        // offset shifts mid-series, so occurrences are not a uniform 24h apart in
        // UTC. the EXDATE we write is a UTC datetime; this proves it still matches
        // an occurrence the provider expanded in local (New York) time.
        val zone = ZoneId.of("America/New_York")
        val dtStart = ZonedDateTime.of(2035, 3, 10, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val winStart = dtStart - dayMs
        val winEnd = dtStart + 5 * dayMs
        val parentId = insertDailySeriesTz(count = 4, dtStartMillis = dtStart, timezone = zone.id)

        val baseline = visibleBegins(winStart, winEnd).sorted()
        assertEquals("baseline: non-UTC series expands to 4 occurrences", 4, baseline.size)

        val target = baseline[1] // occurrence #2, on the far side of the DST shift
        val ok =
            cr.deleteEventScoped(
                eventId = parentId,
                scope = RecurringEditScope.ThisInstance,
                instanceMillis = target,
                parentAllDay = false,
            )
        assertTrue("provider write reported success", ok)
        assertEquals(
            "only the targeted non-UTC occurrence is excluded; the rest survive",
            baseline.filterNot { it == target },
            visibleBegins(winStart, winEnd).sorted(),
        )
    }

    @Test
    fun deleteThisInstance_onMissingParent_failsCleanly() = runBlocking {
        val ok =
            cr.deleteEventScoped(
                eventId = nonexistentEventId(),
                scope = RecurringEditScope.ThisInstance,
                instanceMillis = anchor + dayMs,
                parentAllDay = false,
            )
        assertFalse("a single-occurrence delete on a missing parent reports failure, not success", ok)
    }

    @Test
    fun editThisInstance_onMissingParent_rollsBackTheOneOff() = runBlocking {
        // insert-first ordering means a missing parent inserts the one-off then
        // fails the EXDATE write; the rollback must remove the one-off so a failed
        // edit leaves no orphan event behind.
        val movedStart = anchor + dayMs
        val draft =
            EventDraft(
                calendarId = calendarId,
                title = "Orphan",
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
                eventId = nonexistentEventId(),
                draft = draft,
                scope = RecurringEditScope.ThisInstance,
                instanceMillis = movedStart,
                parentAllDay = false,
            )
        assertTrue("a single-occurrence edit on a missing parent reports failure", rowId == null)
        assertTrue("the inserted one-off is rolled back, leaving no orphan", visibleBegins().isEmpty())
    }

    // "this and following" is the riskiest scope: it truncates the parent and
    // inserts a fresh split, so a wrong COUNT silently over- or under-generates
    // occurrences in a way only the provider's expansion reveals.

    @Test
    fun editThisAndFollowing_countSeries_keepsEveryOccurrence() = runBlocking {
        val parentRrule = "FREQ=DAILY;COUNT=10"
        val parentId = insertDailySeriesWithRrule(parentRrule)
        val wideEnd = anchor + 11 * dayMs
        assertEquals("baseline: ten occurrences", 10, visibleBegins(windowStart, wideEnd).size)

        val splitAt = anchor + 3 * dayMs // occurrence #4
        val newId =
            cr.updateEventScoped(
                eventId = parentId,
                draft = dailyDraft(title = "Future", startMillis = splitAt, rrule = parentRrule),
                scope = RecurringEditScope.ThisAndFollowing,
                instanceMillis = splitAt,
                parentRrule = parentRrule,
                parentAllDay = false,
            )
        assertTrue("split reported success with a new split row", newId != null && newId > 0 && newId != parentId)

        val begins = visibleBegins(windowStart, wideEnd).sorted()
        assertEquals(
            "every original occurrence survives, none duplicated or dropped",
            (0 until 10).map { anchor + it * dayMs },
            begins,
        )
        assertEquals("the first three stay on the parent", 3, countWithTitle(windowStart, wideEnd, "Series"))
        assertEquals("the remaining seven carry the edit", 7, countWithTitle(windowStart, wideEnd, "Future"))
    }

    @Test
    fun editThisAndFollowing_untilSeries_splitStaysUntilBounded() = runBlocking {
        // an UNTIL-bounded parent must not have its split rewritten with a COUNT,
        // or the future series regenerates a fixed number of occurrences and
        // overruns the original bound.
        val parentRrule = "FREQ=DAILY;UNTIL=${untilUtc(anchor + 9 * dayMs)}"
        val parentId = insertDailySeriesWithRrule(parentRrule)
        val wideEnd = anchor + 11 * dayMs
        assertEquals(
            "baseline: ten daily occurrences up to the UNTIL bound",
            10,
            visibleBegins(windowStart, wideEnd).size,
        )

        val splitAt = anchor + 3 * dayMs
        val newId =
            cr.updateEventScoped(
                eventId = parentId,
                draft = dailyDraft(title = "Future", startMillis = splitAt, rrule = parentRrule),
                scope = RecurringEditScope.ThisAndFollowing,
                instanceMillis = splitAt,
                parentRrule = parentRrule,
                parentAllDay = false,
            )
        assertTrue("split reported success", newId != null && newId > 0)

        val splitRrule = eventRrule(newId!!).orEmpty()
        assertFalse("the split series must not gain a COUNT", splitRrule.contains("COUNT", ignoreCase = true))
        assertTrue("the split series stays UNTIL-bounded", splitRrule.contains("UNTIL", ignoreCase = true))
        assertEquals(
            "no over-generation past the UNTIL bound",
            (0 until 10).map { anchor + it * dayMs },
            visibleBegins(windowStart, wideEnd).sorted(),
        )
    }

    @Test
    fun editThisAndFollowing_atFirstOccurrence_updatesInPlaceWithoutDuplicate() = runBlocking {
        val parentRrule = "FREQ=DAILY;COUNT=5"
        val parentId = insertDailySeriesWithRrule(parentRrule)
        val before = eventRowCount()
        assertEquals("baseline: five occurrences", 5, visibleBegins().size)

        val resultId =
            cr.updateEventScoped(
                eventId = parentId,
                draft = dailyDraft(title = "Renamed", startMillis = anchor, rrule = parentRrule),
                scope = RecurringEditScope.ThisAndFollowing,
                instanceMillis = anchor, // first occurrence covers the whole series
                parentRrule = parentRrule,
                parentAllDay = false,
            )
        assertEquals("a first-occurrence split updates the parent in place", parentId, resultId)
        assertEquals("no duplicate split row is inserted", before, eventRowCount())
        assertEquals("all five occurrences remain", 5, visibleBegins().size)
        assertEquals("the edit applies to the whole series", 5, countWithTitle(windowStart, windowEnd, "Renamed"))
    }

    @Test
    fun editThisAndFollowing_onMissingParent_rollsBackTheSplit() = runBlocking {
        // parent row gone: the split inserts, then the parent truncation write
        // fails. the split must roll back so a failed op leaves no orphan series
        // instead of a silent duplicate.
        val splitAt = anchor + 2 * dayMs
        val resultId =
            cr.updateEventScoped(
                eventId = nonexistentEventId(),
                draft = dailyDraft(title = "Orphan", startMillis = splitAt, rrule = "FREQ=DAILY;COUNT=3"),
                scope = RecurringEditScope.ThisAndFollowing,
                instanceMillis = splitAt,
                parentRrule = "FREQ=DAILY;COUNT=5",
                parentAllDay = false,
            )
        assertTrue("a split against a missing parent reports failure", resultId == null)
        assertTrue("the inserted split is rolled back, leaving no orphan", visibleBegins().isEmpty())
    }

    // visible occurrences in the window for the test calendar, excluding any the
    // provider marks STATUS_CANCELED (those must not reach the calendar views).
    private fun visibleBegins(start: Long = windowStart, end: Long = windowEnd): List<Long> =
        readInstances(start, end).filter { it.status.isVisible() }.map { it.begin }

    private fun titlesAt(begin: Long): List<String> =
        readInstances().filter { it.begin == begin && it.status.isVisible() }.map { it.title }

    private fun Int?.isVisible(): Boolean = this != CalendarContract.Events.STATUS_CANCELED

    private data class Row(val begin: Long, val title: String, val status: Int?)

    private fun readInstances(start: Long = windowStart, end: Long = windowEnd): List<Row> {
        val rows = mutableListOf<Row>()
        cr.query(
            instancesUriFor(start, end),
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

    private fun insertDailySeriesWithRrule(rrule: String): Long {
        val values =
            ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, "Series")
                put(CalendarContract.Events.DTSTART, anchor)
                put(CalendarContract.Events.DURATION, "PT1H")
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                put(CalendarContract.Events.RRULE, rrule)
            }
        val uri = cr.insert(CalendarContract.Events.CONTENT_URI, values) ?: error("series insert failed")
        return ContentUris.parseId(uri)
    }

    private fun dailyDraft(title: String, startMillis: Long, rrule: String): EventDraft = EventDraft(
        calendarId = calendarId,
        title = title,
        description = null,
        location = null,
        startMillis = startMillis,
        endMillis = startMillis + 3_600_000L,
        allDay = false,
        eventTimezone = "UTC",
        rrule = rrule,
    )

    private fun countWithTitle(start: Long, end: Long, title: String): Int =
        readInstances(start, end).count { it.status.isVisible() && it.title == title }

    // total Events rows on the test calendar; a split must not leave a duplicate.
    private fun eventRowCount(): Int = cr.query(
        CalendarContract.Events.CONTENT_URI,
        arrayOf(CalendarContract.Events._ID),
        "${CalendarContract.Events.CALENDAR_ID} = ?",
        arrayOf(calendarId.toString()),
        null,
    )?.use { it.count } ?: 0

    private fun eventRrule(eventId: Long): String? = cr.query(
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
        arrayOf(CalendarContract.Events.RRULE),
        null,
        null,
        null,
    )?.use { if (it.moveToFirst()) it.getString(0) else null }

    private fun untilUtc(millis: Long): String = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)
        .format(Instant.ofEpochMilli(millis))

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

    private fun insertDailySeriesTz(count: Int, dtStartMillis: Long, timezone: String): Long {
        val values =
            ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, "TzSeries")
                put(CalendarContract.Events.DTSTART, dtStartMillis)
                put(CalendarContract.Events.DURATION, "PT1H")
                put(CalendarContract.Events.EVENT_TIMEZONE, timezone)
                put(CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=$count")
            }
        val uri = cr.insert(CalendarContract.Events.CONTENT_URI, values) ?: error("tz event insert failed")
        return ContentUris.parseId(uri)
    }

    // one past the largest event id the provider holds: guaranteed absent, so a
    // scoped op against it exercises the "parent row is gone" failure path.
    private fun nonexistentEventId(): Long {
        var maxId = 0L
        cr.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            null,
            null,
            "${CalendarContract.Events._ID} DESC",
        )?.use { c ->
            val idx = c.getColumnIndexOrThrow(CalendarContract.Events._ID)
            if (c.moveToFirst()) maxId = c.getLong(idx)
        }
        return maxId + 1
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
