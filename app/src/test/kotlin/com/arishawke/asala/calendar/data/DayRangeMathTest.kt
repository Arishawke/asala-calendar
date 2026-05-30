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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private const val HOUR_MILLIS = 3_600_000L
private const val DAY_MILLIS = 24 * HOUR_MILLIS

class DayRangeMathTest {
    // A non-DST day pair in America/New_York. The query window covers
    // exactly 24h of UTC time.
    @Test
    fun `non DST day in NY is 24h wide`() {
        val (start, end) =
            dayRangeMillis(
                startDate = LocalDate.of(2026, 1, 15),
                endExclusive = LocalDate.of(2026, 1, 16),
                zone = ZoneId.of("America/New_York"),
            )
        assertEquals(DAY_MILLIS, end - start)
    }

    // 2026-03-08 is the spring-forward day in America/New_York. The local
    // wall clock jumps 02:00 -> 03:00, so a midnight-to-midnight window
    // covers only 23h of UTC time. If the math ever returns 24h, recurring
    // events on that day will silently shift in display.
    @Test
    fun `spring forward day in NY is 23h wide`() {
        val (start, end) =
            dayRangeMillis(
                startDate = LocalDate.of(2026, 3, 8),
                endExclusive = LocalDate.of(2026, 3, 9),
                zone = ZoneId.of("America/New_York"),
            )
        assertEquals(23 * HOUR_MILLIS, end - start)
    }

    // 2026-11-01 is the fall-back day in America/New_York. The local wall
    // clock repeats 01:00-02:00, so a midnight-to-midnight window covers
    // 25h of UTC time.
    @Test
    fun `fall back day in NY is 25h wide`() {
        val (start, end) =
            dayRangeMillis(
                startDate = LocalDate.of(2026, 11, 1),
                endExclusive = LocalDate.of(2026, 11, 2),
                zone = ZoneId.of("America/New_York"),
            )
        assertEquals(25 * HOUR_MILLIS, end - start)
    }

    // UTC has no DST. Every day is 24h regardless of date. Useful sanity
    // check that the zone parameter is actually consulted.
    @Test
    fun `every day in UTC is 24h wide across the DST window`() {
        val zone = ZoneId.of("UTC")
        val anchor = LocalDate.of(2026, 3, 7)
        repeat(5) { offset ->
            val (start, end) =
                dayRangeMillis(
                    startDate = anchor.plusDays(offset.toLong()),
                    endExclusive = anchor.plusDays(offset + 1L),
                    zone = zone,
                )
            assertEquals(DAY_MILLIS, end - start)
        }
    }

    // Locks in the half-open convention: end of a day is next-day-midnight,
    // not 23:59:59.999. A closed upper bound would drop events scheduled at
    // 23:59 from Day view (the ghost-event report).
    @Test
    fun `dayRange endsAtNextDayMidnight notLastMillisecondOfDay`() {
        val zone = ZoneId.of("UTC")
        val date = LocalDate.of(2026, 3, 15)
        val (_, end) =
            dayRangeMillis(
                startDate = date,
                endExclusive = date.plusDays(1),
                zone = zone,
            )
        val expectedEnd =
            date
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        assertEquals(expectedEnd, end)
    }

    // Regression for the user-reported ghost event: a 23:59 event was
    // missing from Day view. The range must include events that start at
    // 23:59 on the queried date.
    @Test
    fun `dayRange includesEventStartingAt2359`() {
        val zone = ZoneId.of("UTC")
        val date = LocalDate.of(2026, 3, 15)
        val (start, end) =
            dayRangeMillis(
                startDate = date,
                endExclusive = date.plusDays(1),
                zone = zone,
            )
        val eventStart =
            ZonedDateTime
                .of(2026, 3, 15, 23, 59, 30, 0, zone)
                .toInstant()
                .toEpochMilli()
        assertTrue(eventStart >= start && eventStart < end)
    }

    // The upper bound is exclusive. An event starting exactly at next-day
    // midnight belongs to the next day, not the queried one.
    @Test
    fun `dayRange excludesEventStartingAtNextDayMidnight`() {
        val zone = ZoneId.of("UTC")
        val date = LocalDate.of(2026, 3, 15)
        val (_, end) =
            dayRangeMillis(
                startDate = date,
                endExclusive = date.plusDays(1),
                zone = zone,
            )
        val eventStart =
            ZonedDateTime
                .of(2026, 3, 16, 0, 0, 0, 0, zone)
                .toInstant()
                .toEpochMilli()
        assertTrue(eventStart >= end)
    }

    // Spring-forward day shortens the UTC span to 23h, but the 23:59 local
    // boundary case must still resolve correctly. If end-of-day were
    // computed as start + 24h instead of next-day-midnight in zone, this
    // would fail.
    @Test
    fun `dayRange dstSpringForward 2359EventStillIncluded`() {
        val zone = ZoneId.of("America/Los_Angeles")
        val date = LocalDate.of(2026, 3, 8)
        val (start, end) =
            dayRangeMillis(
                startDate = date,
                endExclusive = date.plusDays(1),
                zone = zone,
            )
        val eventStart =
            ZonedDateTime
                .of(2026, 3, 8, 23, 59, 30, 0, zone)
                .toInstant()
                .toEpochMilli()
        assertTrue(eventStart >= start && eventStart < end)
    }
}
