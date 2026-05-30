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
import java.time.ZoneOffset

class ScheduleNowLineTest {
    private val utc = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 5, 29)

    private fun millis(hour: Int, minute: Int = 0): Long =
        day.atTime(hour, minute).atZone(utc).toInstant().toEpochMilli()

    private fun timedRow(id: Long, startHour: Int, endHour: Int): ScheduleRow {
        val start = millis(startHour)
        val end = millis(endHour)
        return ScheduleRow(
            event = EventItem(
                instanceId = id,
                eventId = id,
                calendarId = 1L,
                title = "t$id",
                startMillis = start,
                endMillis = end,
                allDay = false,
                displayColor = 0,
            ),
            displayStartMillis = start,
            displayEndMillis = end,
        )
    }

    private fun allDayRow(id: Long): ScheduleRow = ScheduleRow(
        event = EventItem(
            instanceId = id,
            eventId = id,
            calendarId = 1L,
            title = "all$id",
            startMillis = day.atStartOfDay(utc).toInstant().toEpochMilli(),
            endMillis = day.plusDays(1).atStartOfDay(utc).toInstant().toEpochMilli(),
            allDay = true,
            displayColor = 0,
        ),
    )

    @Test
    fun `line falls below a long in-progress event, not at its start`() {
        // The reported bug: 1pm-6pm event in progress at 4:26pm put the
        // line at the 1pm mark because end-based logic matched it first.
        // Start-based logic keeps the in-progress event above the line.
        val rows = listOf(
            timedRow(1L, startHour = 13, endHour = 18), // 1pm-6pm, in progress
            timedRow(2L, startHour = 19, endHour = 20), // 7pm, upcoming
        )
        assertEquals(1, scheduleNowLineIndex(rows, millis(16, 26)))
    }

    @Test
    fun `line sits above events that have not started yet`() {
        val rows = listOf(
            timedRow(1L, startHour = 9, endHour = 10), // finished
            timedRow(2L, startHour = 14, endHour = 15), // upcoming
            timedRow(3L, startHour = 16, endHour = 17), // upcoming
        )
        assertEquals(1, scheduleNowLineIndex(rows, millis(11)))
    }

    @Test
    fun `event starting exactly now counts as upcoming`() {
        val rows = listOf(
            timedRow(1L, startHour = 9, endHour = 10),
            timedRow(2L, startHour = 12, endHour = 13),
        )
        assertEquals(1, scheduleNowLineIndex(rows, millis(12)))
    }

    @Test
    fun `line goes to the bottom when every timed event has started`() {
        val rows = listOf(
            timedRow(1L, startHour = 9, endHour = 10),
            timedRow(2L, startHour = 11, endHour = 23), // long, still running
        )
        assertEquals(rows.size, scheduleNowLineIndex(rows, millis(22)))
    }

    @Test
    fun `all-day rows stay above the line`() {
        // All-day rows lead the section; the divider must land after them
        // even when nothing timed has started yet.
        val rows = listOf(
            allDayRow(1L),
            timedRow(2L, startHour = 14, endHour = 15),
        )
        assertEquals(1, scheduleNowLineIndex(rows, millis(8)))
    }
}
