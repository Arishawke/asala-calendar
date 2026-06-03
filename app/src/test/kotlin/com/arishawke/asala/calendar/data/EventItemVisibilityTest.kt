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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class EventItemVisibilityTest {
    private val zone = ZoneId.of("America/Los_Angeles")

    private fun atStartOfDay(date: LocalDate): Long = ZonedDateTime.of(date, java.time.LocalTime.MIDNIGHT, zone)
        .toInstant()
        .toEpochMilli()

    // Pre-fix regression: a single-day all-day event on June 1 has
    // endMillis = start of June 2. The old inclusive filter !e.isBefore(June 2)
    // returned true and the event ghost-appeared on June 2 in day and
    // week views. isVisibleIn must treat the all-day end as exclusive.
    @Test
    fun `all-day event does not bleed onto its exclusive end day`() {
        val event = EventItem(
            instanceId = 1L,
            eventId = 1L,
            calendarId = 1L,
            title = "Birthday",
            startMillis = atStartOfDay(LocalDate.of(2026, 6, 1)),
            endMillis = atStartOfDay(LocalDate.of(2026, 6, 2)),
            allDay = true,
            displayColor = 0,
        )

        assertTrue(event.isVisibleIn(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1), zone))
        assertFalse(event.isVisibleIn(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 2), zone))
    }

    // Timed events keep inclusive end-date semantics: a meeting that
    // ends at 13:00 on June 1 is visible on June 1.
    @Test
    fun `timed event is visible on its end-date`() {
        val event = EventItem(
            instanceId = 1L,
            eventId = 1L,
            calendarId = 1L,
            title = "Lunch",
            startMillis = ZonedDateTime.of(LocalDate.of(2026, 6, 1), java.time.LocalTime.of(12, 0), zone)
                .toInstant().toEpochMilli(),
            endMillis = ZonedDateTime.of(LocalDate.of(2026, 6, 1), java.time.LocalTime.of(13, 0), zone)
                .toInstant().toEpochMilli(),
            allDay = false,
            displayColor = 0,
        )

        assertTrue(event.isVisibleIn(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1), zone))
        assertFalse(event.isVisibleIn(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 2), zone))
    }

    // Week-range filter: the all-day exclusivity has to apply to the
    // FIRST day of the range too, or a multi-day all-day event from the
    // prior week bleeds into the current week's filter when its end is
    // exactly the current week's first day.
    @Test
    fun `all-day event ending on range first-day is excluded`() {
        val event = EventItem(
            instanceId = 1L,
            eventId = 1L,
            calendarId = 1L,
            title = "Vacation",
            startMillis = atStartOfDay(LocalDate.of(2026, 5, 25)),
            endMillis = atStartOfDay(LocalDate.of(2026, 6, 1)),
            allDay = true,
            displayColor = 0,
        )

        assertFalse(event.isVisibleIn(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zone))
        assertTrue(event.isVisibleIn(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 31), zone))
    }

    private fun atUtcStartOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun allDay(start: LocalDate, endExclusive: LocalDate) = EventItem(
        instanceId = 1L,
        eventId = 1L,
        calendarId = 1L,
        title = "AllDay",
        startMillis = atUtcStartOfDay(start),
        endMillis = atUtcStartOfDay(endExclusive),
        allDay = true,
        displayColor = 0,
    )

    private fun timed(start: LocalDate, startTime: LocalTime, end: LocalDate, endTime: LocalTime) = EventItem(
        instanceId = 1L,
        eventId = 1L,
        calendarId = 1L,
        title = "Timed",
        startMillis = ZonedDateTime.of(start, startTime, zone).toInstant().toEpochMilli(),
        endMillis = ZonedDateTime.of(end, endTime, zone).toInstant().toEpochMilli(),
        allDay = false,
        displayColor = 0,
    )

    // lastDate is the inclusive last day an event occupies, the per-day-expansion
    // companion to isVisibleIn. all-day's exclusive end falls out of the same rule.
    @Test
    fun `lastDate of an all-day event excludes its exclusive end day`() {
        // occupies Jun 1, 2, 3; endMillis is the start of Jun 4
        val event = allDay(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 4))
        assertEquals(LocalDate.of(2026, 6, 3), event.lastDate(zone))
    }

    @Test
    fun `lastDate of a timed event ending at 13_00 is that day`() {
        val event = timed(LocalDate.of(2026, 6, 1), LocalTime.of(12, 0), LocalDate.of(2026, 6, 1), LocalTime.of(13, 0))
        assertEquals(LocalDate.of(2026, 6, 1), event.lastDate(zone))
    }

    // the bug guard: a 22:00 -> 00:00 event must not occupy the next day, matching
    // clipToDay's endMillis-1 rule, so the month widget agrees with Day/Schedule.
    @Test
    fun `lastDate of a timed event ending exactly at midnight stays on the prior day`() {
        val event = timed(LocalDate.of(2026, 6, 1), LocalTime.of(22, 0), LocalDate.of(2026, 6, 2), LocalTime.MIDNIGHT)
        assertEquals(LocalDate.of(2026, 6, 1), event.lastDate(zone))
    }

    @Test
    fun `lastDate of a malformed end-before-start collapses to the start day`() {
        val event = timed(LocalDate.of(2026, 6, 10), LocalTime.of(10, 0), LocalDate.of(2026, 6, 9), LocalTime.of(10, 0))
        assertEquals(LocalDate.of(2026, 6, 10), event.lastDate(zone))
    }
}
