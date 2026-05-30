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
import org.junit.Assert.assertSame
import org.junit.Test

class EventItemColorOverrideTest {
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

    // Empty maps are a no-op and return the same list instance so we
    // don't pay an unnecessary allocation on the common case.
    @Test
    fun `empty maps return the same instance`() {
        val events = listOf(event(10L, 1L, 0xFF000001.toInt()), event(20L, 2L, 0xFF000002.toInt()))
        val result = events.applyColorOverrides(emptyMap(), emptyMap())
        assertSame(events, result)
    }

    @Test
    fun `calendar override remaps only matching calendarIds`() {
        val events = listOf(event(10L, 1L, 0xFF111111.toInt()), event(20L, 2L, 0xFF222222.toInt()))
        val result = events.applyColorOverrides(
            calendarOverrides = mapOf(1L to 0xFFAAAAAA.toInt()),
            eventOverrides = emptyMap(),
        )
        assertEquals(0xFFAAAAAA.toInt(), result[0].displayColor)
        assertEquals(0xFF222222.toInt(), result[1].displayColor)
    }

    // Precedence: event override beats calendar override.
    @Test
    fun `event override wins over calendar override`() {
        val events = listOf(event(10L, 1L, 0xFF111111.toInt()))
        val result = events.applyColorOverrides(
            calendarOverrides = mapOf(1L to 0xFFAAAAAA.toInt()),
            eventOverrides = mapOf(10L to 0xFFBBBBBB.toInt()),
        )
        assertEquals(0xFFBBBBBB.toInt(), result[0].displayColor)
    }

    // Per-event override is keyed on eventId (not instanceId), so every
    // instance of a recurring event picks up the override.
    @Test
    fun `event override applies to all instances of a recurring eventId`() {
        val recurringEventId = 99L
        val sharedCalendarId = 1L
        val instanceA = event(recurringEventId, sharedCalendarId, 0xFF111111.toInt())
            .copy(instanceId = 9900L, startMillis = 1_000)
        val instanceB = event(recurringEventId, sharedCalendarId, 0xFF111111.toInt())
            .copy(instanceId = 9901L, startMillis = 2_000)
        val result = listOf(instanceA, instanceB).applyColorOverrides(
            calendarOverrides = emptyMap(),
            eventOverrides = mapOf(recurringEventId to 0xFFBBBBBB.toInt()),
        )
        assertEquals(0xFFBBBBBB.toInt(), result[0].displayColor)
        assertEquals(0xFFBBBBBB.toInt(), result[1].displayColor)
    }

    @Test
    fun `non-matching maps leave every event alone`() {
        val events = listOf(event(10L, 1L, 0xFF111111.toInt()))
        val result = events.applyColorOverrides(
            calendarOverrides = mapOf(99L to 0xFFAAAAAA.toInt()),
            eventOverrides = mapOf(99L to 0xFFBBBBBB.toInt()),
        )
        assertEquals(0xFF111111.toInt(), result[0].displayColor)
    }
}
