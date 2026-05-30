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

// Mirrors EventItemColorOverrideTest but for the detail-sheet resolver.
// Pure-Kotlin precedence test, no ViewModel needed; the function it
// covers is the same one AppViewModelSheetState.openEventDetail calls.
class EventDetailColorTest {
    private val providerColor = 0xFF111111.toInt()
    private val calendarOverride = 0xFFAAAAAA.toInt()
    private val eventOverride = 0xFFBBBBBB.toInt()
    private val detail = EventDetail(
        eventId = 42L,
        calendarId = 7L,
        title = "Lunch",
        description = null,
        location = null,
        startMillis = 0L,
        endMillis = 60_000L,
        allDay = false,
        eventTimezone = "UTC",
        rrule = null,
        displayColor = providerColor,
        calendarDisplayName = "Personal",
        reminderMinutesBefore = null,
    )

    @Test
    fun `empty overrides returns detail displayColor`() {
        assertEquals(
            providerColor,
            resolveEventDetailColor(detail, eventOverrides = emptyMap(), calendarOverrides = emptyMap()),
        )
    }

    @Test
    fun `calendar override wins over detail displayColor`() {
        assertEquals(
            calendarOverride,
            resolveEventDetailColor(
                detail,
                eventOverrides = emptyMap(),
                calendarOverrides = mapOf(detail.calendarId to calendarOverride),
            ),
        )
    }

    @Test
    fun `event override wins over calendar override`() {
        assertEquals(
            eventOverride,
            resolveEventDetailColor(
                detail,
                eventOverrides = mapOf(detail.eventId to eventOverride),
                calendarOverrides = mapOf(detail.calendarId to calendarOverride),
            ),
        )
    }
}
