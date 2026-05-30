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
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers the composed pipeline now used by all five event-loading view
// models (Month / Week / Day / Schedule / Search). Each test pins one
// invariant of `hidden -> applyColorOverrides` composition.
class VisibilityAndOverridesTest {
    private fun event(eventId: Long, calendarId: Long, color: Int): EventItem = EventItem(
        instanceId = eventId * 100,
        eventId = eventId,
        calendarId = calendarId,
        title = "Event $eventId",
        startMillis = 0,
        endMillis = 60_000,
        allDay = false,
        displayColor = color,
    )

    @Test
    fun `hidden calendars drop before overrides apply`() {
        val events = listOf(
            event(10L, 1L, 0xFF111111.toInt()),
            event(20L, 2L, 0xFF222222.toInt()),
        )
        val result = events.filteredAndRecolored(
            hidden = setOf(1L),
            calendarOverrides = mapOf(1L to 0xFFAAAAAA.toInt()),
            eventOverrides = emptyMap(),
        )
        assertEquals(1, result.size)
        assertEquals(20L, result[0].eventId)
    }

    @Test
    fun `event override beats calendar override after filter`() {
        val events = listOf(event(10L, 1L, 0xFF111111.toInt()))
        val result = events.filteredAndRecolored(
            hidden = emptySet(),
            calendarOverrides = mapOf(1L to 0xFFAAAAAA.toInt()),
            eventOverrides = mapOf(10L to 0xFFBBBBBB.toInt()),
        )
        assertEquals(0xFFBBBBBB.toInt(), result[0].displayColor)
    }

    @Test
    fun `empty hidden and empty overrides preserves displayColor`() {
        val events = listOf(event(10L, 1L, 0xFF111111.toInt()))
        val result = events.filteredAndRecolored(
            hidden = emptySet(),
            calendarOverrides = emptyMap(),
            eventOverrides = emptyMap(),
        )
        assertEquals(0xFF111111.toInt(), result[0].displayColor)
    }

    @Test
    fun `hidden set with no matches passes everything through`() {
        val events = listOf(event(10L, 1L, 0xFF111111.toInt()))
        val result = events.filteredAndRecolored(
            hidden = setOf(99L),
            calendarOverrides = emptyMap(),
            eventOverrides = emptyMap(),
        )
        assertTrue(result.size == 1 && result[0].eventId == 10L)
    }
}
