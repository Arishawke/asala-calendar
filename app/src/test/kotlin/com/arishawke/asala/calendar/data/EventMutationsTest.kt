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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventMutationsTest {
    // The parent-truncation update MUST include DTSTART, not just RRULE:
    // CalendarProvider only rebuilds the Instances table when DTSTART is in the
    // update delta, otherwise stale occurrences linger past the new UNTIL and
    // duplicate the split series. This is the whole point of the fix.
    @Test
    fun `parent truncation includes DTSTART so the provider rebuilds instances`() {
        val map =
            parentTruncationMap(parentDtStart = 1_700_000_000_000L, newRrule = "FREQ=DAILY;UNTIL=20260604T035459Z")
        assertEquals(1_700_000_000_000L, map[CalendarContract.Events.DTSTART])
        assertEquals("FREQ=DAILY;UNTIL=20260604T035459Z", map[CalendarContract.Events.RRULE])
    }

    // The null path is only hit if the parent vanished between the edit trigger
    // and this read; then we fall back to an rrule-only write (the old behavior:
    // stale occurrences linger until the next provider refresh).
    @Test
    fun `parent truncation omits DTSTART only when the parent start is unknown`() {
        val map = parentTruncationMap(parentDtStart = null, newRrule = "FREQ=WEEKLY;UNTIL=20260604T035459Z")
        assertFalse(map.containsKey(CalendarContract.Events.DTSTART))
        assertEquals("FREQ=WEEKLY;UNTIL=20260604T035459Z", map[CalendarContract.Events.RRULE])
    }

    // The "this and following" COUNT split keeps the parent occurrences that
    // fall strictly before the split instance, so the future series carries the
    // remaining COUNT. Instances range queries are end-inclusive, so an instance
    // whose start equals the split point IS the split itself and must not be
    // counted, or the edited run loses one occurrence.
    @Test
    fun `countInstancesBefore counts starts strictly before the split point`() {
        assertEquals(2, countInstancesBefore(listOf(10L, 20L, 30L, 40L), beforeMillis = 30L))
    }

    @Test
    fun `countInstancesBefore excludes an instance exactly at the split point`() {
        assertEquals(0, countInstancesBefore(listOf(30L), beforeMillis = 30L))
    }

    @Test
    fun `countInstancesBefore counts all when every start precedes the split`() {
        assertEquals(3, countInstancesBefore(listOf(1L, 2L, 3L), beforeMillis = 100L))
    }

    @Test
    fun `countInstancesBefore is zero for no instances`() {
        assertEquals(0, countInstancesBefore(emptyList(), beforeMillis = 50L))
    }

    // The COUNT budget is divided across parent + future only when the user left
    // the inherited count untouched. If they retyped it (or switched to UNTIL /
    // open-ended), the split keeps their value as-is instead of subtracting the
    // kept occurrences from it.
    @Test
    fun `shouldReduceSplitCount is true when the split keeps the parent's count`() {
        assertTrue(shouldReduceSplitCount("FREQ=DAILY;COUNT=10", "FREQ=DAILY;COUNT=10"))
    }

    @Test
    fun `shouldReduceSplitCount is false when the user retyped the count`() {
        assertFalse(shouldReduceSplitCount("FREQ=DAILY;COUNT=10", "FREQ=DAILY;COUNT=5"))
    }

    @Test
    fun `shouldReduceSplitCount is false when the parent is not count-bounded`() {
        assertFalse(shouldReduceSplitCount("FREQ=DAILY;UNTIL=20261231T235959Z", "FREQ=DAILY;COUNT=10"))
        assertFalse(shouldReduceSplitCount("FREQ=DAILY", "FREQ=DAILY;COUNT=10"))
    }

    @Test
    fun `shouldReduceSplitCount is false when the split dropped the count`() {
        assertFalse(shouldReduceSplitCount("FREQ=DAILY;COUNT=10", "FREQ=DAILY;UNTIL=20261231T235959Z"))
    }

    // Deleting "this and following" from the first occurrence (instance start at
    // or before the parent DTSTART) must delete the whole series, not truncate it
    // to a UNTIL-before-DTSTART shell. The update path already guards this; the
    // delete path must match.
    @Test
    fun `shouldDeleteEntireSeries is true when deleting from the first occurrence`() {
        assertTrue(shouldDeleteEntireSeries(instanceMillis = 1_000L, parentDtStart = 1_000L))
    }

    @Test
    fun `shouldDeleteEntireSeries is true when the instance precedes the parent start`() {
        assertTrue(shouldDeleteEntireSeries(instanceMillis = 1_000L, parentDtStart = 2_000L))
    }

    @Test
    fun `shouldDeleteEntireSeries is false for a later occurrence`() {
        assertFalse(shouldDeleteEntireSeries(instanceMillis = 2_000L, parentDtStart = 1_000L))
    }

    @Test
    fun `shouldDeleteEntireSeries is false when the parent start is unknown`() {
        assertFalse(shouldDeleteEntireSeries(instanceMillis = 1_000L, parentDtStart = null))
    }
}
