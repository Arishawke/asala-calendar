/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.data

import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OccasionEventTest {
    @Test fun `uri round-trips`() {
        val o = Occasion(42, "Alice", OccasionType.Birthday, 6, 15, 1990)
        assertEquals("asala://occasion/42/Birthday", occasionCustomAppUri(o))
        assertEquals(42L to OccasionType.Birthday, parseOccasionUri("asala://occasion/42/Birthday"))
        assertNull(parseOccasionUri("mailto:x@y.z"))
        assertNull(parseOccasionUri(null))
    }

    @Test fun `dtstart is midnight UTC of the given date`() {
        val ms = occasionDtStartMillis(6, 15, 1990)
        val d = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        assertEquals(java.time.LocalDate.of(1990, 6, 15), d)
    }

    @Test fun `no-year uses sentinel`() {
        val ms = occasionDtStartMillis(12, 25, null)
        val y = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate().year
        assertEquals(OCCASION_NO_YEAR_SENTINEL, y)
    }

    @Test fun `draft is all-day yearly with custom-app id and P1D`() {
        val o = Occasion(42, "Alice", OccasionType.Birthday, 6, 15, 1990)
        val m = occasionEventDraft(
            o,
            calendarId = 7,
            appPackage = "com.x",
            title = "Alice's birthday",
            name = "Alice",
        ).toMap()
        assertEquals(7L, m[CalendarContract.Events.CALENDAR_ID])
        assertEquals(1, m[CalendarContract.Events.ALL_DAY])
        assertEquals("UTC", m[CalendarContract.Events.EVENT_TIMEZONE])
        assertEquals("FREQ=YEARLY", m[CalendarContract.Events.RRULE])
        assertEquals("P1D", m[CalendarContract.Events.DURATION])
        assertEquals("asala://occasion/42/Birthday", m[CalendarContract.Events.CUSTOM_APP_URI])
        assertEquals("com.x", m[CalendarContract.Events.CUSTOM_APP_PACKAGE])
        assertEquals("Alice", m[CalendarContract.Events.DESCRIPTION])
    }
}
