/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderEventSnapshotTest {
    private val start = 1_700_000_000_000L
    private val fallbackTitle = "(No title)"

    // Recurring rows store DURATION not DTEND. Prior to this fix the
    // receiver only projected DTEND, so endMillis was 0L and the
    // notification body rendered "1 January 1970" as the end of the
    // time range. Regression case.
    @Test
    fun `recurring row with null DTEND and one-hour duration yields dtStart plus one hour`() {
        val snapshot =
            buildReminderEventSnapshot(
                title = "Standup",
                location = null,
                allDay = false,
                startMillis = start,
                dtEnd = null,
                durationIso8601 = "PT1H",
                displayColor = 0,
                fallbackTitle = fallbackTitle,
            )
        assertEquals(start + 3_600_000L, snapshot.endMillis)
    }

    // Single-occurrence rows write DTEND directly. Trust it without
    // re-deriving from DURATION; DURATION may be stale on those rows.
    @Test
    fun `single-occurrence row with present DTEND returns DTEND unchanged`() {
        val end = start + 1_800_000L
        val snapshot =
            buildReminderEventSnapshot(
                title = "Standup",
                location = null,
                allDay = false,
                startMillis = start,
                dtEnd = end,
                durationIso8601 = null,
                displayColor = 0,
                fallbackTitle = fallbackTitle,
            )
        assertEquals(end, snapshot.endMillis)
    }

    // Blank or null titles fall back so the notification body never
    // renders an empty headline.
    @Test
    fun `blank title falls back to provided fallback`() {
        val snapshot =
            buildReminderEventSnapshot(
                title = "   ",
                location = null,
                allDay = false,
                startMillis = start,
                dtEnd = start + 1L,
                durationIso8601 = null,
                displayColor = 0,
                fallbackTitle = fallbackTitle,
            )
        assertEquals(fallbackTitle, snapshot.title)
    }

    // Empty-string location collapses to null so the notification
    // layout skips the location row entirely rather than rendering a
    // blank line.
    @Test
    fun `blank location collapses to null`() {
        val snapshot =
            buildReminderEventSnapshot(
                title = "Standup",
                location = "",
                allDay = false,
                startMillis = start,
                dtEnd = start + 1L,
                durationIso8601 = null,
                displayColor = 0,
                fallbackTitle = fallbackTitle,
            )
        assertEquals(null, snapshot.location)
    }
}
