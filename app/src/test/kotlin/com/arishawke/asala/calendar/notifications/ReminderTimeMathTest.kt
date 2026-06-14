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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderTimeMathTest {
    private val ny = ZoneId.of("America/New_York")

    private fun atZone(year: Int, month: Int, day: Int, hour: Int = 0, min: Int = 0): Long = LocalDateTime
        .of(year, month, day, hour, min)
        .atZone(ny)
        .toInstant()
        .toEpochMilli()

    @Test
    fun `timed event 10 min before subtracts 600000 ms`() {
        val start = atZone(2026, 6, 1, 9, 0)
        val expected = atZone(2026, 6, 1, 8, 50)
        assertEquals(expected, ReminderTimeMath.computeAlarmTime(start, allDay = false, minutesBefore = 10, zone = ny))
    }

    @Test
    fun `all-day at time of event fires at 9am same day not midnight`() {
        val start = atZone(2026, 6, 1, 0, 0)
        val expected = atZone(2026, 6, 1, 9, 0)
        assertEquals(expected, ReminderTimeMath.computeAlarmTime(start, allDay = true, minutesBefore = 0, zone = ny))
    }

    @Test
    fun `all-day 1 day before fires at 9am previous day`() {
        val start = atZone(2026, 6, 1, 0, 0)
        val expected = atZone(2026, 5, 31, 9, 0)
        assertEquals(expected, ReminderTimeMath.computeAlarmTime(start, allDay = true, minutesBefore = 1440, zone = ny))
    }

    @Test
    fun `all-day 30 min before still anchors to 9am day of event`() {
        val start = atZone(2026, 6, 1, 0, 0)
        val expected = atZone(2026, 6, 1, 9, 0)
        assertEquals(expected, ReminderTimeMath.computeAlarmTime(start, allDay = true, minutesBefore = 30, zone = ny))
    }

    @Test
    fun `all-day reminder respects DST spring-forward boundary`() {
        val start =
            LocalDate
                .of(2026, 3, 9)
                .atStartOfDay(ny)
                .toInstant()
                .toEpochMilli()
        val expected =
            LocalDateTime
                .of(2026, 3, 8, 9, 0)
                .atZone(ny)
                .toInstant()
                .toEpochMilli()
        assertEquals(expected, ReminderTimeMath.computeAlarmTime(start, allDay = true, minutesBefore = 1440, zone = ny))
    }

    // CalendarContract stores all-day events at 00:00 UTC by convention.
    // Earlier tests used local NY midnight as the input, which happens to
    // round-trip to the same date under either zone-extraction strategy
    // (NY midnight is still the same UTC date for negative offsets).
    // The bug only surfaces when startMillis really is UTC midnight: then
    // interpreting in NY zone reads as 7pm/8pm the prior day, and the
    // alarm fires a day early. The next two tests pin the UTC-midnight
    // path explicitly so a regression to atZone(zone) would fail loudly.
    @Test
    fun `UTC-midnight all-day event extracts the same date in NY despite negative offset`() {
        val utcMidnight = LocalDate
            .of(2026, 6, 1)
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val expected = atZone(2026, 6, 1, 9, 0)
        assertEquals(
            expected,
            ReminderTimeMath.computeAlarmTime(utcMidnight, allDay = true, minutesBefore = 0, zone = ny),
        )
    }

    @Test
    fun `UTC-midnight all-day event 1 day before fires at 9am previous local day`() {
        val utcMidnight = LocalDate
            .of(2026, 6, 1)
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val expected = atZone(2026, 5, 31, 9, 0)
        assertEquals(
            expected,
            ReminderTimeMath.computeAlarmTime(utcMidnight, allDay = true, minutesBefore = 1440, zone = ny),
        )
    }

    @Test
    fun `all-day reminder respects DST fall-back boundary`() {
        val start =
            LocalDate
                .of(2026, 11, 2)
                .atStartOfDay(ny)
                .toInstant()
                .toEpochMilli()
        val expected =
            LocalDateTime
                .of(2026, 11, 1, 9, 0)
                .atZone(ny)
                .toInstant()
                .toEpochMilli()
        assertEquals(expected, ReminderTimeMath.computeAlarmTime(start, allDay = true, minutesBefore = 1440, zone = ny))
    }

    // all-day lead time floors to whole days: 2000 minutes (1.39 days) offsets
    // one day, firing 9am the day before, not part-way. Pins the integer-division
    // (minutesBefore / 1440) contract for the 1-to-2-day range.
    @Test
    fun `all-day lead time between one and two days floors to one day`() {
        val start = atZone(2026, 6, 1, 0, 0)
        val expected = atZone(2026, 5, 31, 9, 0)
        assertEquals(expected, ReminderTimeMath.computeAlarmTime(start, allDay = true, minutesBefore = 2000, zone = ny))
    }

    // a 2880-minute (exactly 2-day) lead time offsets two whole days.
    @Test
    fun `all-day lead time of two days offsets two days`() {
        val start = atZone(2026, 6, 1, 0, 0)
        val expected = atZone(2026, 5, 30, 9, 0)
        assertEquals(expected, ReminderTimeMath.computeAlarmTime(start, allDay = true, minutesBefore = 2880, zone = ny))
    }
}
