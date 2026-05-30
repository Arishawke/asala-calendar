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

class ReminderColorOverrideTest {
    private val providerColor = 0xFF111111.toInt()
    private val calendarOverride = 0xFFAAAAAA.toInt()
    private val eventOverride = 0xFFBBBBBB.toInt()
    private val eventId = 42L
    private val calendarId = 7L

    @Test
    fun `empty overrides returns provider color`() {
        val resolved = resolveReminderColor(
            providerColor = providerColor,
            eventId = eventId,
            calendarId = calendarId,
            eventOverrides = emptyMap(),
            calendarOverrides = emptyMap(),
        )
        assertEquals(providerColor, resolved)
    }

    // No matching key in either map leaves the provider color untouched.
    @Test
    fun `non-matching overrides returns provider color`() {
        val resolved = resolveReminderColor(
            providerColor = providerColor,
            eventId = eventId,
            calendarId = calendarId,
            eventOverrides = mapOf(99L to eventOverride),
            calendarOverrides = mapOf(99L to calendarOverride),
        )
        assertEquals(providerColor, resolved)
    }

    @Test
    fun `calendar override wins over provider color`() {
        val resolved = resolveReminderColor(
            providerColor = providerColor,
            eventId = eventId,
            calendarId = calendarId,
            eventOverrides = emptyMap(),
            calendarOverrides = mapOf(calendarId to calendarOverride),
        )
        assertEquals(calendarOverride, resolved)
    }

    // Precedence: event > calendar > provider. Both present, event wins.
    @Test
    fun `event override wins over calendar override`() {
        val resolved = resolveReminderColor(
            providerColor = providerColor,
            eventId = eventId,
            calendarId = calendarId,
            eventOverrides = mapOf(eventId to eventOverride),
            calendarOverrides = mapOf(calendarId to calendarOverride),
        )
        assertEquals(eventOverride, resolved)
    }

    @Test
    fun `event override alone wins over provider color`() {
        val resolved = resolveReminderColor(
            providerColor = providerColor,
            eventId = eventId,
            calendarId = calendarId,
            eventOverrides = mapOf(eventId to eventOverride),
            calendarOverrides = emptyMap(),
        )
        assertEquals(eventOverride, resolved)
    }
}
