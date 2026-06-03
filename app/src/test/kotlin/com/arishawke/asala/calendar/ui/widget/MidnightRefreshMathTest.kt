package com.arishawke.asala.calendar.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class MidnightRefreshMathTest {
    private val ny = ZoneId.of("America/New_York")

    private fun millis(dateTime: LocalDateTime, zone: ZoneId) = dateTime.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `returns start of the next day in zone`() {
        val now = millis(LocalDateTime.of(2026, 6, 3, 9, 30), ny)
        val expected = LocalDate.of(2026, 6, 4).atStartOfDay(ny).toInstant().toEpochMilli()
        assertEquals(expected, MidnightRefreshMath.nextLocalMidnight(now, ny))
    }

    @Test
    fun `one minute before midnight rolls to the coming midnight`() {
        val now = millis(LocalDateTime.of(2026, 6, 3, 23, 59), ny)
        val expected = LocalDate.of(2026, 6, 4).atStartOfDay(ny).toInstant().toEpochMilli()
        assertEquals(expected, MidnightRefreshMath.nextLocalMidnight(now, ny))
    }

    @Test
    fun `next midnight is strictly after now`() {
        val now = millis(LocalDateTime.of(2026, 3, 8, 1, 0), ny) // US spring-forward day
        assertTrue(MidnightRefreshMath.nextLocalMidnight(now, ny) > now)
    }
}
