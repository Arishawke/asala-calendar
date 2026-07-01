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
import android.content.Context
import android.provider.CalendarContract
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
import java.time.LocalDate
import java.time.ZoneOffset

// Task 0 spike: de-risks two provider assumptions the contact-occasions
// feature rests on, against the real CalendarProvider (JVM unit tests cannot
// model provider expansion or column round-tripping). Uses a throwaway LOCAL
// calendar (deleted in tearDown) created through the real CalendarRepository
// write path so the spike proves the path the feature will actually use.
@RunWith(AndroidJUnit4::class)
class OccasionProviderSpikeTest {
    @get:Rule
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val customAppUri = "asala://occasion/999/Birthday"
    private val occasionMonth = 6
    private val occasionDay = 15
    private val expansionWindowDays = 400L
    private val dayMs = 86_400_000L

    private lateinit var context: Context
    private lateinit var cr: ContentResolver
    private lateinit var repository: CalendarRepository
    private var calendarId: Long? = null

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        cr = context.contentResolver
        repository = CalendarRepository(cr)
        calendarId = repository.createLocalCalendar("Asala Spike", 0xFF999999.toInt())
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        calendarId?.let { repository.deleteLocalCalendar(it) }
    }

    @Test
    fun customAppUriRoundTripsAndSentinelYearEventExpands() {
        val calId = checkNotNull(calendarId) { "temp calendar creation failed" }
        val dtStart =
            LocalDate.of(OCCASION_NO_YEAR_SENTINEL, occasionMonth, occasionDay)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        val values =
            ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.TITLE, "Birthday")
                put(CalendarContract.Events.DTSTART, dtStart)
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.DURATION, "P1D")
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
                put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
                put(CalendarContract.Events.CUSTOM_APP_URI, customAppUri)
            }
        val uri = cr.insert(CalendarContract.Events.CONTENT_URI, values) ?: error("event insert failed")
        val eventId = ContentUris.parseId(uri)

        // assert 1 (Deviation 1): CUSTOM_APP_URI/CUSTOM_APP_PACKAGE survive a
        // read-back through the plain events URI, so they can stand in for
        // _SYNC_ID as the reconcile identity key without a sync-adapter write.
        cr.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            arrayOf(CalendarContract.Events.CUSTOM_APP_URI),
            null,
            null,
            null,
        )!!.use {
            assertTrue("read-back cursor is empty", it.moveToFirst())
            assertEquals("CUSTOM_APP_URI round-trips through the plain events URI", customAppUri, it.getString(0))
        }

        // assert 2 (Deviation 2): a pre-1970 sentinel-year all-day FREQ=YEARLY
        // event still expands into a current-window Instances row, so no-year
        // occasions can anchor DTSTART on OCCASION_NO_YEAR_SENTINEL.
        val now = System.currentTimeMillis()
        val windowEnd = now + expansionWindowDays * dayMs
        cr.query(
            instancesUriFor(now, windowEnd),
            arrayOf(CalendarContract.Instances.EVENT_ID),
            "${CalendarContract.Instances.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )!!.use {
            assertTrue("sentinel-year yearly event must expand into the next $expansionWindowDays days", it.count >= 1)
        }
    }
}
