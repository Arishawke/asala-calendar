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
import org.junit.Assert.assertNull
import org.junit.Test

class SnoozeResolutionTest {
    // Happy path: the alert row resolves to (eventId, originalMinutes).
    // The caller uses both to schedule the snooze alarm and to seed the
    // re-fired notification's reminder-minutes display.
    @Test
    fun `alert id resolves to event and minutes`() {
        val resolved =
            SnoozeResolution.resolve(
                alertId = 7L,
                alertLookup = { id -> if (id == 7L) 42L to 15 else null },
                intentEventId = -1L,
                intentOriginalMinutes = 30,
            )
        assertEquals(42L to 15, resolved)
    }

    // Provider hiccup or stale alertId: row lookup misses but the intent
    // still carries the event id and firing offset. Use both so snooze cancels
    // the right shade entry despite the transient provider state.
    @Test
    fun `lookup miss falls back to intent event id and minutes`() {
        val resolved =
            SnoozeResolution.resolve(
                alertId = 7L,
                alertLookup = { null },
                intentEventId = 42L,
                intentOriginalMinutes = 30,
            )
        assertEquals(42L to 30, resolved)
    }

    // alertId<=0 means the original ensureCalendarAlert insert failed; do
    // not even attempt the lookup. Trust the intent fallback values.
    @Test
    fun `negative alert id falls back to intent event id and minutes`() {
        val resolved =
            SnoozeResolution.resolve(
                alertId = -1L,
                alertLookup = { error("must not be called when alertId<=0") },
                intentEventId = 42L,
                intentOriginalMinutes = 60,
            )
        assertEquals(42L to 60, resolved)
    }

    // Worst case: alertId and intent event id are both invalid. Return
    // null so the receiver bails instead of scheduling a phantom alarm
    // against eventId=0 (which would silently never fire).
    @Test
    fun `negative alert id and negative intent event id returns null`() {
        val resolved =
            SnoozeResolution.resolve(
                alertId = -1L,
                alertLookup = { null },
                intentEventId = -1L,
                intentOriginalMinutes = 30,
            )
        assertNull(resolved)
    }
}
