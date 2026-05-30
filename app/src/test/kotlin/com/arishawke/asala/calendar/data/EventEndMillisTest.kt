/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EventEndMillisTest {
    // Single-occurrence rows store DTEND directly; trust it without
    // re-deriving from DURATION (DURATION may be null or stale on those
    // rows). Inverting this would shift end times for normal events.
    @Test
    fun `present DTEND wins over any duration`() {
        val end =
            EventEndMillis.compute(
                dtStart = 1_000_000L,
                dtEnd = 2_000_000L,
                durationIso8601 = "PT1H",
            )
        assertEquals(2_000_000L, end)
    }

    // Recurring rows write DURATION not DTEND. Without this branch the
    // editor and detail sheet would render zero-length events on every
    // recurring instance, which is what the v0.6.x ghost-event symptom
    // looked like.
    @Test
    fun `null DTEND with valid duration falls back to dtStart plus duration`() {
        val end =
            EventEndMillis.compute(
                dtStart = 1_000_000L,
                dtEnd = null,
                durationIso8601 = "PT1H",
            )
        assertEquals(1_000_000L + 3_600_000L, end)
    }

    // The detail sheet must never crash on a malformed DURATION; instead
    // it falls back to a zero-length end. The user can still see the row
    // and edit it; alternative (throwing) would block the detail screen.
    @Test
    fun `null DTEND with garbage duration falls back to dtStart`() {
        val end =
            EventEndMillis.compute(
                dtStart = 1_000_000L,
                dtEnd = null,
                durationIso8601 = "not-a-duration",
            )
        assertEquals(1_000_000L, end)
    }

    // Some legitimate provider rows have neither DTEND nor DURATION (very
    // old rows from third-party sync adapters). Treat as zero-length end.
    @Test
    fun `null DTEND with null duration falls back to dtStart`() {
        val end =
            EventEndMillis.compute(
                dtStart = 1_000_000L,
                dtEnd = null,
                durationIso8601 = null,
            )
        assertEquals(1_000_000L, end)
    }
}
