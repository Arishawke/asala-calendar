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
import org.junit.Test

class EventMutationsTest {
    private fun draft(rrule: String? = "FREQ=DAILY") = EventDraft(
        calendarId = 3L,
        title = "Standup",
        description = null,
        location = null,
        startMillis = 1_700_000_000_000L,
        endMillis = 1_700_003_600_000L,
        allDay = false,
        eventTimezone = "America/New_York",
        rrule = rrule,
    )

    // A this-instance edit becomes a non-recurring exception bound to the
    // parent series' slot. Pre-fix this field assembly lived inline in
    // updateEventScoped and was untested.
    @Test
    fun `this-instance exception binds to parent slot and drops recurrence`() {
        val map = thisInstanceExceptionMap(
            draft = draft(rrule = "FREQ=DAILY"),
            parentEventId = 7L,
            instanceMillis = 1_700_086_400_000L,
            parentAllDay = false,
        )
        assertEquals(7L, map[CalendarContract.Events.ORIGINAL_ID])
        assertEquals(1_700_086_400_000L, map[CalendarContract.Events.ORIGINAL_INSTANCE_TIME])
        assertEquals(0, map[CalendarContract.Events.ORIGINAL_ALL_DAY])
        assertEquals(1_700_003_600_000L, map[CalendarContract.Events.DTEND])
        assertFalse(map.containsKey(CalendarContract.Events.RRULE))
        assertFalse(map.containsKey(CalendarContract.Events.DURATION))
    }

    // All-day parents need ORIGINAL_ALL_DAY=1 or the provider cannot match the
    // exception against the UTC-midnight slot and the original still shows.
    @Test
    fun `all-day this-instance exception marks ORIGINAL_ALL_DAY`() {
        val map = thisInstanceExceptionMap(
            draft = draft(rrule = "FREQ=WEEKLY"),
            parentEventId = 9L,
            instanceMillis = 1_700_000_000_000L,
            parentAllDay = true,
        )
        assertEquals(1, map[CalendarContract.Events.ORIGINAL_ALL_DAY])
        // exception's own ALL_DAY follows the draft, not the parent
        assertEquals(0, map[CalendarContract.Events.ALL_DAY])
    }

    // A draft with no rrule (e.g. the series was made non-recurring elsewhere
    // and synced): the remove() calls are no-ops and DTEND is set from the
    // draft. The exception must still bind to the parent slot.
    @Test
    fun `non-recurring draft still binds to parent slot with DTEND`() {
        val map = thisInstanceExceptionMap(
            draft = draft(rrule = null),
            parentEventId = 4L,
            instanceMillis = 1_700_086_400_000L,
            parentAllDay = false,
        )
        assertEquals(4L, map[CalendarContract.Events.ORIGINAL_ID])
        assertEquals(1_700_086_400_000L, map[CalendarContract.Events.ORIGINAL_INSTANCE_TIME])
        assertEquals(1_700_003_600_000L, map[CalendarContract.Events.DTEND])
        assertFalse(map.containsKey(CalendarContract.Events.RRULE))
        assertFalse(map.containsKey(CalendarContract.Events.DURATION))
    }

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
}
