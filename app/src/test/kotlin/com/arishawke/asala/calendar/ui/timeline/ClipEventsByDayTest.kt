/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

import com.arishawke.asala.calendar.data.EventItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ClipEventsByDayTest {
    private val zone = ZoneId.of("America/New_York")

    private fun atLocal(date: LocalDate, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant().toEpochMilli()

    private fun timed(id: Long, start: Long, end: Long): EventItem = EventItem(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = "t$id",
        startMillis = start,
        endMillis = end,
        allDay = false,
        displayColor = 0,
    )

    private fun allDay(id: Long, date: LocalDate): EventItem = EventItem(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = "ad$id",
        startMillis = atLocal(date, 0),
        endMillis = atLocal(date.plusDays(1), 0),
        allDay = true,
        displayColor = 0,
    )

    private val mon = LocalDate.of(2026, 6, 1)
    private val tue = LocalDate.of(2026, 6, 2)
    private val wed = LocalDate.of(2026, 6, 3)
    private val days = listOf(mon, tue, wed)

    @Test fun all_days_are_present_as_keys() {
        val result = clipEventsByDay(emptyList(), days, zone)
        assertEquals(days.toSet(), result.keys)
    }

    @Test fun timed_event_on_one_day_appears_only_in_that_day() {
        val ev = timed(1, atLocal(tue, 9), atLocal(tue, 10))
        val result = clipEventsByDay(listOf(ev), days, zone)

        assertTrue("mon should be empty", result.getValue(mon).isEmpty())
        assertEquals(1, result.getValue(tue).size)
        assertEquals(ev, result.getValue(tue).first().event)
        assertTrue("wed should be empty", result.getValue(wed).isEmpty())
    }

    @Test fun midnight_crosser_appears_in_both_crossing_days() {
        // starts mon 23:30, ends tue 00:30 - crosses into tue
        val ev = timed(2, atLocal(mon, 23, 30), atLocal(tue, 0, 30))
        val result = clipEventsByDay(listOf(ev), days, zone)

        val monClips = result.getValue(mon)
        val tueClips = result.getValue(tue)
        assertEquals("event must appear in mon", 1, monClips.size)
        assertEquals("event must appear in tue", 1, tueClips.size)
        assertEquals(ev, monClips.first().event)
        assertEquals(ev, tueClips.first().event)
        // verify the clips match what clipToDay would produce individually
        assertEquals(clipToDay(ev, mon, zone), monClips.first())
        assertEquals(clipToDay(ev, tue, zone), tueClips.first())
    }

    @Test fun all_day_event_is_excluded_from_every_day() {
        val ad = allDay(3, tue)
        val result = clipEventsByDay(listOf(ad), days, zone)

        days.forEach { d ->
            assertTrue("all-day event must not appear in $d", result.getValue(d).isEmpty())
        }
    }

    @Test fun mixed_list_filters_all_day_and_routes_timed_correctly() {
        val timedEv = timed(4, atLocal(wed, 14), atLocal(wed, 15))
        val adEv = allDay(5, wed)
        val result = clipEventsByDay(listOf(timedEv, adEv), days, zone)

        assertTrue(result.getValue(mon).isEmpty())
        assertTrue(result.getValue(tue).isEmpty())
        val wedClips = result.getValue(wed)
        assertEquals(1, wedClips.size)
        assertEquals(timedEv, wedClips.first().event)
    }

    @Test fun result_matches_per_day_clipToDay_calls() {
        val ev1 = timed(6, atLocal(mon, 10), atLocal(mon, 11))
        val ev2 = timed(7, atLocal(tue, 22, 45), atLocal(wed, 0, 15))
        val events = listOf(ev1, ev2)
        val result = clipEventsByDay(events, days, zone)

        days.forEach { d ->
            val expected = events.filter { !it.allDay }.mapNotNull { clipToDay(it, d, zone) }
            assertEquals("day $d clips must match direct clipToDay calls", expected, result.getValue(d))
        }
    }
}
