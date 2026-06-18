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
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderReArmTest {
    private val ny = ZoneId.of("America/New_York")

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(ny).toInstant().toEpochMilli()

    @Test
    fun `next re-arm time is the next local midnight`() {
        assertEquals(millis(2026, 6, 2, 0, 0), nextReArmTime(millis(2026, 6, 1, 10, 0), ny))
    }

    @Test
    fun `next re-arm time advances from just before midnight`() {
        assertEquals(millis(2026, 6, 2, 0, 0), nextReArmTime(millis(2026, 6, 1, 23, 59), ny))
    }

    // zone-aware: 2026-03-08 is US spring-forward (02:00 -> 03:00). A naive
    // now + 24h would land at 02:00 the next day, not midnight. The re-arm must be
    // the actual next local midnight.
    @Test
    fun `next re-arm time is zone-aware across spring forward`() {
        assertEquals(millis(2026, 3, 9, 0, 0), nextReArmTime(millis(2026, 3, 8, 1, 0), ny))
    }
}
