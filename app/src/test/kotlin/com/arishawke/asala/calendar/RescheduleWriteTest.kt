/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar

import com.arishawke.asala.calendar.data.EventDetail
import com.arishawke.asala.calendar.data.RecurringEditScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RescheduleWriteTest {
    private fun detail(eventId: Long = 7L, rrule: String? = null): EventDetail = EventDetail(
        eventId = eventId,
        calendarId = 1L,
        title = "Lunch",
        description = null,
        location = null,
        startMillis = 1_700_000_000_000L,
        endMillis = 1_700_003_600_000L,
        allDay = false,
        eventTimezone = "America/New_York",
        rrule = rrule,
        displayColor = 0,
        calendarDisplayName = "Personal",
        reminderMinutesBefore = null,
    )

    // The drag chip moves optimistically; on a provider rejection (read-only
    // calendar, permission revoked mid-drag) the move must snap back exactly once,
    // or the chip strands at a time nothing was written to.
    @Test
    fun `rejected update emits exactly one revert`() = runBlocking {
        val reverts = mutableListOf<Long>()
        rescheduleWrite(
            detail = detail(eventId = 7L),
            instanceMillis = 1_700_000_000_000L,
            newStart = 1_700_003_600_000L,
            newEnd = 1_700_007_200_000L,
            scope = RecurringEditScope.AllEvents,
            updateEvent = { _, _, _, _, _, _ -> null },
            onRevert = { reverts += it },
        )
        assertEquals(listOf(7L), reverts)
    }

    @Test
    fun `accepted update emits no revert`() = runBlocking {
        val reverts = mutableListOf<Long>()
        rescheduleWrite(
            detail = detail(eventId = 7L),
            instanceMillis = 1_700_000_000_000L,
            newStart = 1_700_003_600_000L,
            newEnd = 1_700_007_200_000L,
            scope = RecurringEditScope.AllEvents,
            updateEvent = { _, _, _, _, _, _ -> 7L },
            onRevert = { reverts += it },
        )
        assertTrue("no revert when the provider accepts the move", reverts.isEmpty())
    }
}
