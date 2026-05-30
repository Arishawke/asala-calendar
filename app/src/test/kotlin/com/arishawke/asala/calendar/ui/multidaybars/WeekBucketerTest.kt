/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.multidaybars

import com.arishawke.asala.calendar.data.EventItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class WeekBucketerTest {
    private val utc = ZoneOffset.UTC

    // Helper: build an all-day EventItem covering [firstDay, lastDay] inclusive.
    // CalendarContract stores all-day endMillis as start-of-day AFTER the
    // last visible day, so we pass lastDay+1.
    private fun allDay(id: Long, title: String, firstDay: LocalDate, lastDay: LocalDate): EventItem = EventItem(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = title,
        startMillis = firstDay.atStartOfDay(utc).toInstant().toEpochMilli(),
        endMillis = lastDay.plusDays(1).atStartOfDay(utc).toInstant().toEpochMilli(),
        allDay = true,
        displayColor = 0xFF1A73E8.toInt(),
    )

    private fun timed(id: Long, day: LocalDate): EventItem = EventItem(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = "timed",
        startMillis = day.atStartOfDay(utc).toInstant().toEpochMilli(),
        endMillis = day.plusDays(1).atStartOfDay(utc).toInstant().toEpochMilli(),
        allDay = false,
        displayColor = 0xFF34A853.toInt(),
    )

    @Test
    fun `single-day all-day event is NOT bucketed by default`() {
        // Single-day all-day events render inline inside their DayCell in
        // Month view; bucketing them as a week-spanning bar made every
        // other day's timed chips shift down. The default skips them.
        val weekStart = LocalDate.of(2026, 5, 24) // Sunday
        val events = listOf(allDay(1L, "Lunch", LocalDate.of(2026, 5, 26), LocalDate.of(2026, 5, 26)))
        val segments = WeekBucketer.bucketize(events, weekStart, utc)
        assertEquals(0, segments.size)
    }

    @Test
    fun `single-day all-day event IS bucketed when includeSingleDay is true`() {
        // Week view's AllDayRow owns its own all-day surface, so single-
        // day all-day events need to render there as a one-column bar.
        // Opting in via includeSingleDay = true brings them back.
        val weekStart = LocalDate.of(2026, 5, 24) // Sunday
        val events = listOf(allDay(1L, "Lunch", LocalDate.of(2026, 5, 26), LocalDate.of(2026, 5, 26)))
        val segments = WeekBucketer.bucketize(events, weekStart, utc, includeSingleDay = true)
        assertEquals(1, segments.size)
        val s = segments[0]
        // Tue May 26 is col 2 of a Sun-start week.
        assertEquals(2, s.startCol)
        assertEquals(2, s.endCol)
        assertFalse(s.isContinuedLeft)
        assertFalse(s.isContinuedRight)
    }

    @Test
    fun `outside-week filtering still applies with includeSingleDay = true`() {
        val weekStart = LocalDate.of(2026, 5, 24)
        val events = listOf(allDay(1L, "Next month", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15)))
        val segments = WeekBucketer.bucketize(events, weekStart, utc, includeSingleDay = true)
        assertEquals(0, segments.size)
    }

    @Test
    fun `event fully inside one week produces one segment with correct columns`() {
        val weekStart = LocalDate.of(2026, 5, 24) // Sunday
        // Mon 25 - Wed 27 inclusive (cols 1..3)
        val events = listOf(allDay(1L, "Conf", LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 27)))
        val segments = WeekBucketer.bucketize(events, weekStart, utc)
        assertEquals(1, segments.size)
        val s = segments[0]
        assertEquals(1, s.startCol)
        assertEquals(3, s.endCol)
        assertFalse(s.isContinuedLeft)
        assertFalse(s.isContinuedRight)
    }

    @Test
    fun `event crossing right edge of week is clipped and flagged continued-right`() {
        val weekStart = LocalDate.of(2026, 5, 24) // Sunday
        // Fri 29 - Mon Jun 1 inclusive. In this week: Fri (col 5) and Sat (col 6).
        val events = listOf(allDay(1L, "Trip", LocalDate.of(2026, 5, 29), LocalDate.of(2026, 6, 1)))
        val segments = WeekBucketer.bucketize(events, weekStart, utc)
        assertEquals(1, segments.size)
        val s = segments[0]
        assertEquals(5, s.startCol)
        assertEquals(6, s.endCol)
        assertFalse(s.isContinuedLeft)
        assertTrue(s.isContinuedRight)
    }

    @Test
    fun `event crossing left edge of week is clipped and flagged continued-left`() {
        val weekStart = LocalDate.of(2026, 5, 24) // Sunday
        // Thu 21 - Tue 26 inclusive. In this week: Sun (col 0), Mon (col 1), Tue (col 2).
        val events = listOf(allDay(1L, "Retreat", LocalDate.of(2026, 5, 21), LocalDate.of(2026, 5, 26)))
        val segments = WeekBucketer.bucketize(events, weekStart, utc)
        assertEquals(1, segments.size)
        val s = segments[0]
        assertEquals(0, s.startCol)
        assertEquals(2, s.endCol)
        assertTrue(s.isContinuedLeft)
        assertFalse(s.isContinuedRight)
    }

    @Test
    fun `event covering entire week is clipped on both sides`() {
        val weekStart = LocalDate.of(2026, 5, 24) // Sunday; week ends Sat May 30
        // Wed 20 - Mon Jun 1, fully envelops Sun May 24 - Sat May 30
        val events = listOf(allDay(1L, "All", LocalDate.of(2026, 5, 20), LocalDate.of(2026, 6, 1)))
        val segments = WeekBucketer.bucketize(events, weekStart, utc)
        assertEquals(1, segments.size)
        val s = segments[0]
        assertEquals(0, s.startCol)
        assertEquals(6, s.endCol)
        assertTrue(s.isContinuedLeft)
        assertTrue(s.isContinuedRight)
    }

    @Test
    fun `event outside the requested week produces no segment`() {
        val weekStart = LocalDate.of(2026, 5, 24)
        val events = listOf(allDay(1L, "Next month", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 16)))
        assertEquals(0, WeekBucketer.bucketize(events, weekStart, utc).size)
    }

    @Test
    fun `timed events are excluded`() {
        val weekStart = LocalDate.of(2026, 5, 24)
        val events = listOf(timed(1L, LocalDate.of(2026, 5, 26)))
        assertEquals(0, WeekBucketer.bucketize(events, weekStart, utc).size)
    }

    @Test
    fun `event title and color carry through`() {
        val weekStart = LocalDate.of(2026, 5, 24)
        val events = listOf(allDay(42L, "Vacation", LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 27)))
        val s = WeekBucketer.bucketize(events, weekStart, utc).single()
        assertEquals(42L, s.eventId)
        assertEquals("Vacation", s.title)
        assertEquals(0xFF1A73E8.toInt(), s.color)
    }

    @Test
    fun `multiple events in same week all produce segments`() {
        val weekStart = LocalDate.of(2026, 5, 24)
        val events = listOf(
            allDay(1L, "A", LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 27)),
            allDay(2L, "B", LocalDate.of(2026, 5, 26), LocalDate.of(2026, 5, 28)),
        )
        val segments = WeekBucketer.bucketize(events, weekStart, utc)
        assertEquals(2, segments.size)
    }

    @Test
    fun `all-day events in non-UTC system zone still use UTC for date interpretation`() {
        // CalendarContract stores all-day in UTC. Even if the device is in
        // a non-UTC zone, the date interpretation must use UTC or off-by-one
        // bugs surface near midnight boundaries. Uses a multi-day event since
        // single-day all-day events are intentionally skipped by the bucketer.
        val weekStart = LocalDate.of(2026, 5, 24)
        val event = allDay(1L, "X", LocalDate.of(2026, 5, 26), LocalDate.of(2026, 5, 27))
        // Pass a non-UTC zone to verify the bucketer ignores it for all-day.
        val nyc = ZoneId.of("America/New_York")
        val segments = WeekBucketer.bucketize(listOf(event), weekStart, nyc)
        assertEquals(1, segments.size)
        assertEquals(2, segments[0].startCol)
        assertEquals(3, segments[0].endCol)
    }
}
