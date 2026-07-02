/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// CAL_ACCESS_CONTRIBUTOR (500) is the writable floor; below it the calendar is
// read-only and edit/delete must be hidden so the detail sheet doesn't offer
// actions the provider will reject.
class EventDetailWritabilityTest {
    private fun detail(accessLevel: Int) = EventDetail(
        eventId = 1L,
        calendarId = 1L,
        title = "Holiday",
        description = null,
        location = null,
        startMillis = 0L,
        endMillis = 86_400_000L,
        allDay = true,
        eventTimezone = "UTC",
        rrule = null,
        displayColor = 0,
        calendarDisplayName = "Holidays",
        reminderMinutes = emptyList(),
        accessLevel = accessLevel,
    )

    @Test
    fun `read-only calendar is not writable`() {
        assertFalse(detail(accessLevel = 200).isWritable)
    }

    @Test
    fun `contributor access is writable`() {
        assertTrue(detail(accessLevel = 500).isWritable)
    }

    @Test
    fun `owner access is writable`() {
        assertTrue(detail(accessLevel = 700).isWritable)
    }
}
