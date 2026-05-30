/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EventEditFormStateTest {
    private fun timed(): EventEditFormState = EventEditFormState(
        startDate = LocalDate.of(2026, 5, 24),
        startTime = LocalTime.of(9, 0),
        endDate = LocalDate.of(2026, 5, 24),
        endTime = LocalTime.of(10, 0),
        allDay = false,
    )

    private fun allDay(): EventEditFormState = EventEditFormState(
        startDate = LocalDate.of(2026, 5, 24),
        startTime = LocalTime.MIDNIGHT,
        endDate = LocalDate.of(2026, 5, 26),
        endTime = LocalTime.MIDNIGHT,
        allDay = true,
    )

    // End date earlier than start date: clamp to start date so the
    // editor never holds an invalid range.
    @Test
    fun `withEndDate clamps end date to start when user moves it earlier`() {
        val s = timed().withEndDate(LocalDate.of(2026, 5, 20))
        assertEquals(LocalDate.of(2026, 5, 24), s.endDate)
    }

    // End date later than start date: passes through unchanged.
    @Test
    fun `withEndDate passes a valid later end date through`() {
        val s = timed().withEndDate(LocalDate.of(2026, 5, 30))
        assertEquals(LocalDate.of(2026, 5, 30), s.endDate)
    }

    // Timed end time earlier than start: roll forward to next day so
    // the event ends at the same clock time the next day.
    @Test
    fun `withEndTime rolls end to next day when end goes before start on a timed event`() {
        val s = timed().withEndTime(LocalTime.of(8, 0))
        assertEquals(LocalDate.of(2026, 5, 25), s.endDate)
        assertEquals(LocalTime.of(8, 0), s.endTime)
    }

    // Timed end time equal to start: also rolls (zero-length event is
    // invalid, isEndAfterStart requires strict >).
    @Test
    fun `withEndTime rolls when new end equals start`() {
        val s = timed().withEndTime(LocalTime.of(9, 0))
        assertEquals(LocalDate.of(2026, 5, 25), s.endDate)
    }

    // Timed end time later than start: passes through unchanged.
    @Test
    fun `withEndTime passes a later end time through`() {
        val s = timed().withEndTime(LocalTime.of(11, 0))
        assertEquals(LocalDate.of(2026, 5, 24), s.endDate)
        assertEquals(LocalTime.of(11, 0), s.endTime)
    }

    // All-day events ignore the time component; time-edit is a no-op
    // for the day boundary, so just copy the time through.
    @Test
    fun `withEndTime on all-day leaves date alone and copies time`() {
        val s = allDay().withEndTime(LocalTime.NOON)
        assertEquals(LocalDate.of(2026, 5, 26), s.endDate)
        assertEquals(LocalTime.NOON, s.endTime)
    }

    // First toggle from all-day to timed seeds end = start + default
    // duration. The flag must flip so subsequent toggles preserve any
    // user edits. The loaded date is preserved (would otherwise silently
    // move an existing all-day event to today on toggle off).
    @Test
    fun `withAllDay false on first conversion seeds default duration and preserves date`() {
        val s = allDay().copy(defaultDurationMinutes = 30).withAllDay(false)
        assertEquals(false, s.allDay)
        assertEquals(true, s.convertedFromAllDay)
        assertEquals(LocalDate.of(2026, 5, 24), s.startDate)
        assertEquals(LocalDate.of(2026, 5, 24), s.endDate)
        val seededMinutes = java.time.Duration
            .between(s.startDate.atTime(s.startTime), s.endDate.atTime(s.endTime))
            .toMinutes()
        assertEquals(30L, seededMinutes)
    }

    // After the first conversion, a re-toggle from all-day back to timed
    // preserves whatever the user has since picked, instead of clobbering
    // with the default again. Times are pinned so the test isn't sensitive
    // to wall-clock hour-of-day (nextRoundHour + N can wrap LocalTime
    // around midnight when CI runs in the late evening).
    @Test
    fun `withAllDay false preserves user edits on second conversion`() {
        val seeded = allDay().copy(defaultDurationMinutes = 30).withAllDay(false)
        val edited = seeded.copy(startTime = LocalTime.of(9, 0), endTime = LocalTime.of(13, 0))
        val toggled = edited.copy(allDay = true).withAllDay(false)
        assertEquals(false, toggled.allDay)
        assertEquals(true, toggled.convertedFromAllDay)
        assertEquals(LocalTime.of(9, 0), toggled.startTime)
        assertEquals(LocalTime.of(13, 0), toggled.endTime)
        val finalMinutes = java.time.Duration
            .between(toggled.startDate.atTime(toggled.startTime), toggled.endDate.atTime(toggled.endTime))
            .toMinutes()
        assertEquals(4L * 60L, finalMinutes)
    }

    // Toggling all-day ON should never touch convertedFromAllDay; the flag
    // only tracks first-OFF conversion.
    @Test
    fun `withAllDay true leaves convertedFromAllDay alone`() {
        val s = timed().withAllDay(true)
        assertEquals(true, s.allDay)
        assertEquals(false, s.convertedFromAllDay)
    }
}
