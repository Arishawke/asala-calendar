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
import org.junit.Assert.assertEquals
import org.junit.Test

class EventCancellationTest {
    // The provider requires every field on the insert for a recurring-event
    // exception row. Missing CALENDAR_ID rejects, missing DTSTART/DTEND
    // throws, missing ORIGINAL_INSTANCE_TIME makes the slot stay visible.
    // Pin them all so a future change cannot silently drop one and re-open
    // the "ghost event" symptom from v0.7.0.
    @Test
    fun `cancellation row carries every provider-required field`() {
        val cv =
            EventCancellation.buildMap(
                parentEventId = 42L,
                parentCalendarId = 7L,
                instanceMillis = 1_700_000_000_000L,
                timezoneId = "America/New_York",
            )

        assertEquals(42L, cv[CalendarContract.Events.ORIGINAL_ID])
        assertEquals(1_700_000_000_000L, cv[CalendarContract.Events.ORIGINAL_INSTANCE_TIME])
        assertEquals(
            CalendarContract.Events.STATUS_CANCELED,
            cv[CalendarContract.Events.STATUS],
        )
        assertEquals("America/New_York", cv[CalendarContract.Events.EVENT_TIMEZONE])
        assertEquals(1_700_000_000_000L, cv[CalendarContract.Events.DTSTART])
        assertEquals(1_700_000_000_000L, cv[CalendarContract.Events.DTEND])
        assertEquals(7L, cv[CalendarContract.Events.CALENDAR_ID])
    }

    // DTSTART and DTEND match deliberately: a zero-duration marker is the
    // minimum that satisfies the "DTSTART + DTEND-or-DURATION" rule on an
    // Events insert. A non-zero duration here would leave a stub row
    // visible on the calendar.
    @Test
    fun `cancellation is a zero-duration marker`() {
        val cv =
            EventCancellation.buildMap(
                parentEventId = 1L,
                parentCalendarId = 2L,
                instanceMillis = 1_700_000_000_000L,
                timezoneId = "UTC",
            )

        assertEquals(cv[CalendarContract.Events.DTSTART], cv[CalendarContract.Events.DTEND])
    }

    // The exception row must point at the parent series via ORIGINAL_ID;
    // a write that drops this field would orphan the cancellation and
    // re-spawn the deleted occurrence on the next provider read.
    @Test
    fun `ORIGINAL_ID is set to the parent event id`() {
        val cv =
            EventCancellation.buildMap(
                parentEventId = 99L,
                parentCalendarId = 1L,
                instanceMillis = 0L,
                timezoneId = "UTC",
            )
        assertEquals(99L, cv[CalendarContract.Events.ORIGINAL_ID])
    }

    // For an all-day recurring parent (birthdays, anniversaries) the
    // provider matches the exception against the UTC-midnight slot only
    // when ORIGINAL_ALL_DAY=1, ALL_DAY=1, and EVENT_TIMEZONE=UTC. Without
    // those the cancellation row floats and the original occurrence still
    // appears on the calendar.
    @Test
    fun `cancellation row for all-day parent sets ORIGINAL_ALL_DAY and UTC invariants`() {
        val cv =
            EventCancellation.buildMap(
                parentEventId = 42L,
                parentCalendarId = 7L,
                instanceMillis = 1_700_000_000_000L,
                timezoneId = "America/New_York",
                parentAllDay = true,
            )

        assertEquals(1, cv[CalendarContract.Events.ORIGINAL_ALL_DAY])
        assertEquals(1, cv[CalendarContract.Events.ALL_DAY])
        assertEquals("UTC", cv[CalendarContract.Events.EVENT_TIMEZONE])
    }

    // Timed parent: ORIGINAL_ALL_DAY=0 keeps the row from binding to an
    // all-day slot the provider didn't produce.
    @Test
    fun `cancellation row for timed parent sets ORIGINAL_ALL_DAY = 0`() {
        val cv =
            EventCancellation.buildMap(
                parentEventId = 42L,
                parentCalendarId = 7L,
                instanceMillis = 1_700_000_000_000L,
                timezoneId = "America/New_York",
                parentAllDay = false,
            )

        assertEquals(0, cv[CalendarContract.Events.ORIGINAL_ALL_DAY])
        assertEquals(0, cv[CalendarContract.Events.ALL_DAY])
        assertEquals("America/New_York", cv[CalendarContract.Events.EVENT_TIMEZONE])
    }
}
