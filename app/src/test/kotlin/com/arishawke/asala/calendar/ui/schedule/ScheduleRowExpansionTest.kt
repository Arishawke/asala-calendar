/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.schedule

import com.arishawke.asala.calendar.data.EventItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ScheduleRowExpansionTest {
    private val utc = ZoneOffset.UTC
    private val zone: ZoneId = utc

    private fun allDay(id: Long, firstDay: LocalDate, lastDay: LocalDate): EventItem = EventItem(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = "all$id",
        startMillis = firstDay.atStartOfDay(utc).toInstant().toEpochMilli(),
        endMillis = lastDay.plusDays(1).atStartOfDay(utc).toInstant().toEpochMilli(),
        allDay = true,
        displayColor = 0,
    )

    private fun timed(id: Long, day: LocalDate): EventItem = EventItem(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = "t$id",
        startMillis = day.atStartOfDay(utc).toInstant().toEpochMilli(),
        endMillis = day.plusDays(1).atStartOfDay(utc).toInstant().toEpochMilli(),
        allDay = false,
        displayColor = 0,
    )

    @Test
    fun `single-day all-day produces one row with dayIndex 1 of 1`() {
        val rows = expandToScheduleRows(
            events = listOf(allDay(1L, LocalDate.of(2026, 5, 24), LocalDate.of(2026, 5, 24))),
            zone = zone,
            windowStart = LocalDate.of(2026, 5, 1),
            windowEndExclusive = LocalDate.of(2026, 6, 1),
        )
        assertEquals(1, rows.size)
        assertEquals(1, rows[0].dayIndex)
        assertEquals(1, rows[0].totalDays)
    }

    @Test
    fun `multi-day all-day produces one row per covered day with sequential index`() {
        val rows = expandToScheduleRows(
            events = listOf(allDay(1L, LocalDate.of(2026, 5, 22), LocalDate.of(2026, 5, 25))),
            zone = zone,
            windowStart = LocalDate.of(2026, 5, 1),
            windowEndExclusive = LocalDate.of(2026, 6, 1),
        )
        assertEquals(4, rows.size)
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.dayIndex })
        assertEquals(listOf(4, 4, 4, 4), rows.map { it.totalDays })
    }

    @Test
    fun `rows outside the window are dropped`() {
        // Event starts before the window; window only covers the last 2 days.
        val rows = expandToScheduleRows(
            events = listOf(allDay(1L, LocalDate.of(2026, 5, 22), LocalDate.of(2026, 5, 25))),
            zone = zone,
            windowStart = LocalDate.of(2026, 5, 24),
            windowEndExclusive = LocalDate.of(2026, 6, 1),
        )
        assertEquals(2, rows.size)
        // dayIndex is preserved (3 and 4 of 4) so the user still sees the
        // event's position in its full span.
        assertEquals(listOf(3, 4), rows.map { it.dayIndex })
        assertEquals(listOf(4, 4), rows.map { it.totalDays })
    }

    @Test
    fun `timed events produce a single row with default index`() {
        val rows = expandToScheduleRows(
            events = listOf(timed(1L, LocalDate.of(2026, 5, 24))),
            zone = zone,
            windowStart = LocalDate.of(2026, 5, 1),
            windowEndExclusive = LocalDate.of(2026, 6, 1),
        )
        assertEquals(1, rows.size)
        assertEquals(1, rows[0].dayIndex)
        assertEquals(1, rows[0].totalDays)
    }

    @Test
    fun `rowDate returns the correct day for each multi-day index`() {
        val event = allDay(1L, LocalDate.of(2026, 5, 22), LocalDate.of(2026, 5, 25))
        assertEquals(LocalDate.of(2026, 5, 22), rowDate(ScheduleRow(event, 1, 4), zone))
        assertEquals(LocalDate.of(2026, 5, 23), rowDate(ScheduleRow(event, 2, 4), zone))
        assertEquals(LocalDate.of(2026, 5, 25), rowDate(ScheduleRow(event, 4, 4), zone))
    }

    private fun timedAt(id: Long, startDate: LocalDate, startHour: Int, endDate: LocalDate, endHour: Int): EventItem {
        val start = startDate.atTime(startHour, 0).atZone(utc).toInstant().toEpochMilli()
        val end = endDate.atTime(endHour, 0).atZone(utc).toInstant().toEpochMilli()
        return EventItem(
            instanceId = id,
            eventId = id,
            calendarId = 1L,
            title = "t$id",
            startMillis = start,
            endMillis = end,
            allDay = false,
            displayColor = 0,
        )
    }

    @Test
    fun `timed event entirely on one day produces a single row with no display clipping`() {
        val day = LocalDate.of(2026, 5, 24)
        val rows = expandToScheduleRows(
            events = listOf(timedAt(1L, day, 9, day, 10)),
            zone = zone,
            windowStart = LocalDate.of(2026, 5, 1),
            windowEndExclusive = LocalDate.of(2026, 6, 1),
        )
        assertEquals(1, rows.size)
        val row = rows[0]
        assertEquals(row.event.startMillis, row.displayStartMillis)
        assertEquals(row.event.endMillis, row.displayEndMillis)
        assertEquals(1, row.dayIndex)
        assertEquals(1, row.totalDays)
    }

    @Test
    fun `timed midnight crosser produces one row per covered day with clipped display millis`() {
        val tue = LocalDate.of(2026, 5, 24)
        val wed = LocalDate.of(2026, 5, 25)
        val rows = expandToScheduleRows(
            events = listOf(timedAt(1L, tue, 23, wed, 1)), // 23:00 Tue -> 01:00 Wed
            zone = zone,
            windowStart = LocalDate.of(2026, 5, 1),
            windowEndExclusive = LocalDate.of(2026, 6, 1),
        )
        assertEquals(2, rows.size)

        val tueRow = rows[0]
        val tueDayStart = tue.atStartOfDay(utc).toInstant().toEpochMilli()
        val wedDayStart = wed.atStartOfDay(utc).toInstant().toEpochMilli()
        assertEquals(tueRow.event.startMillis, tueRow.displayStartMillis)
        assertEquals(wedDayStart, tueRow.displayEndMillis)

        val wedRow = rows[1]
        assertEquals(wedDayStart, wedRow.displayStartMillis)
        assertEquals(wedRow.event.endMillis, wedRow.displayEndMillis)

        // Timed crossers carry segment numbers so each slice can show a
        // "N/total" continuation badge (1/2 on Tue, 2/2 on Wed).
        assertEquals(1, tueRow.dayIndex)
        assertEquals(2, tueRow.totalDays)
        assertEquals(2, wedRow.dayIndex)
        assertEquals(2, wedRow.totalDays)
        // Sanity: tueDayStart used only to ensure clipping math holds.
        assert(tueDayStart < wedDayStart)
    }

    @Test
    fun `timed midnight crosser respects the window`() {
        val tue = LocalDate.of(2026, 5, 24)
        val wed = LocalDate.of(2026, 5, 25)
        val rows = expandToScheduleRows(
            events = listOf(timedAt(1L, tue, 23, wed, 1)),
            zone = zone,
            // Window starts on Wed: the Tuesday slice should be dropped.
            windowStart = wed,
            windowEndExclusive = LocalDate.of(2026, 6, 1),
        )
        assertEquals(1, rows.size)
        val wedDayStart = wed.atStartOfDay(utc).toInstant().toEpochMilli()
        assertEquals(wedDayStart, rows[0].displayStartMillis)
    }

    @Test
    fun `rowDate for clipped timed row uses display start for grouping`() {
        val tue = LocalDate.of(2026, 5, 24)
        val wed = LocalDate.of(2026, 5, 25)
        val ev = timedAt(1L, tue, 23, wed, 1)
        val wedDayStart = wed.atStartOfDay(utc).toInstant().toEpochMilli()
        val wedRow = ScheduleRow(
            event = ev,
            displayStartMillis = wedDayStart,
            displayEndMillis = ev.endMillis,
        )
        assertEquals(wed, rowDate(wedRow, zone))
    }
}
