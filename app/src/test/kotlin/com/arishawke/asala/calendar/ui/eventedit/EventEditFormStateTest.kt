/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import com.arishawke.asala.calendar.data.EventDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

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

    // tap-create: a timeline empty-slot tap prefills the exact snapped time and
    // the end honors the default-duration preference; without a time the FAB
    // path keeps its next-round-hour behavior.
    @Test
    fun `forNewEvent uses the tapped time when one is prefilled`() {
        val s = EventEditFormState.forNewEvent(
            defaultDurationMinutes = 45,
            initialStartDate = LocalDate.of(2026, 7, 3),
            initialStartTime = LocalTime.of(14, 15),
        )
        assertEquals(LocalDate.of(2026, 7, 3), s.startDate)
        assertEquals(LocalTime.of(14, 15), s.startTime)
        assertEquals(LocalTime.of(15, 0), s.endTime)
    }

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

    // The reminder re-seed: toggling a NEW event between timed and all-day swaps
    // the reminder only when it still matches the OLD default (a custom pick is
    // left alone), and an EXISTING event's reminder is never touched. The default
    // helpers leave all reminder fields null, so they never exercise this branch.
    @Test
    fun `withAllDay swaps a new event's default reminder from timed to all-day`() {
        val s = timed().copy(
            isNewEvent = true,
            defaultTimedReminderMinutes = 15,
            defaultAllDayReminderMinutes = 540,
            reminderMinutesBefore = 15, // matches the timed default
        )
        assertEquals(540, s.withAllDay(true).reminderMinutesBefore)
    }

    @Test
    fun `withAllDay leaves a custom reminder alone when toggling a new event`() {
        val s = timed().copy(
            isNewEvent = true,
            defaultTimedReminderMinutes = 15,
            defaultAllDayReminderMinutes = 540,
            reminderMinutesBefore = 5, // a custom pick, not the timed default
        )
        assertEquals(5, s.withAllDay(true).reminderMinutesBefore)
    }

    @Test
    fun `withAllDay never re-seeds an existing event's reminder`() {
        // isNewEvent = false: a reminder equal to the timed default must still
        // survive the toggle unchanged (a saved/server-set reminder is sacred).
        val s = timed().copy(
            isNewEvent = false,
            defaultTimedReminderMinutes = 15,
            defaultAllDayReminderMinutes = 540,
            reminderMinutesBefore = 15,
        )
        assertEquals(15, s.withAllDay(true).reminderMinutesBefore)
    }

    // the reverse direction: toggling all-day -> timed swaps the all-day default
    // back to the timed default (the re-seed gate is symmetric).
    @Test
    fun `withAllDay swaps a new event's default reminder from all-day to timed`() {
        val s = allDay().copy(
            isNewEvent = true,
            defaultTimedReminderMinutes = 15,
            defaultAllDayReminderMinutes = 540,
            reminderMinutesBefore = 540, // matches the all-day default
        )
        assertEquals(15, s.withAllDay(false).reminderMinutesBefore)
    }

    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun source(
        startMillis: Long,
        endMillis: Long,
        allDay: Boolean = false,
        rrule: String? = null,
    ): EventDetail = EventDetail(
        eventId = 42L,
        calendarId = 7L,
        title = "Standup",
        description = "notes",
        location = "Room 1",
        startMillis = startMillis,
        endMillis = endMillis,
        allDay = allDay,
        eventTimezone = "UTC",
        rrule = rrule,
        displayColor = 0xFF112233.toInt(),
        calendarDisplayName = "Work",
        reminderMinutesBefore = 10,
    )

    // A duplicate carries the source's content, lands on its calendar, and
    // is marked new so the save path inserts rather than updates.
    @Test
    fun `forDuplicate copies content and marks the form new`() {
        val s = EventEditFormState.forDuplicate(
            source = source(startMillis = utc(2026, 5, 24, 9, 0), endMillis = utc(2026, 5, 24, 10, 0)),
            instanceStartMillis = null,
            defaultDurationMinutes = 60,
            colorOverrideArgb = 0xFFABCDEF.toInt(),
            zone = ZoneOffset.UTC,
        )
        assertEquals("Standup", s.title)
        assertEquals("notes", s.description)
        assertEquals("Room 1", s.location)
        assertEquals(7L, s.selectedCalendarId)
        assertEquals(10, s.reminderMinutesBefore)
        assertEquals(0xFFABCDEF.toInt(), s.colorOverrideArgb)
        assertEquals(true, s.isNewEvent)
        assertEquals(false, s.allDay)
        assertEquals(LocalDate.of(2026, 5, 24), s.startDate)
        assertEquals(LocalTime.of(9, 0), s.startTime)
        assertEquals(LocalTime.of(10, 0), s.endTime)
    }

    // v1 decision: a duplicate is a single one-off. The recurrence rule is
    // dropped so the copy never silently spawns a second infinite series;
    // the user re-enables repeat in the editor if they want it.
    @Test
    fun `forDuplicate drops recurrence to a one-off`() {
        val s = EventEditFormState.forDuplicate(
            source = source(
                startMillis = utc(2026, 5, 24, 9, 0),
                endMillis = utc(2026, 5, 24, 10, 0),
                rrule = "FREQ=WEEKLY",
            ),
            instanceStartMillis = null,
            defaultDurationMinutes = 60,
            zone = ZoneOffset.UTC,
        )
        assertNull(s.recurrenceFrequency)
    }

    // Duplicating a single occurrence of a recurring series seeds the
    // opened instance's date (preserving duration), not the parent DTSTART,
    // so "duplicate this Tuesday" lands on that Tuesday.
    @Test
    fun `forDuplicate of a recurring instance seeds the opened occurrence`() {
        val s = EventEditFormState.forDuplicate(
            source = source(
                startMillis = utc(2026, 5, 24, 9, 0),
                endMillis = utc(2026, 5, 24, 10, 0),
                rrule = "FREQ=WEEKLY",
            ),
            instanceStartMillis = utc(2026, 6, 7, 9, 0),
            defaultDurationMinutes = 60,
            zone = ZoneOffset.UTC,
        )
        assertEquals(LocalDate.of(2026, 6, 7), s.startDate)
        assertEquals(LocalTime.of(9, 0), s.startTime)
        assertEquals(LocalDate.of(2026, 6, 7), s.endDate)
        assertEquals(LocalTime.of(10, 0), s.endTime)
        assertNull(s.recurrenceFrequency)
    }

    // All-day events are stored at UTC midnight with an exclusive end; the
    // form must restore the inclusive last day, extracting in UTC so the
    // date does not slip in negative-offset zones.
    @Test
    fun `forDuplicate of an all-day event restores inclusive UTC dates`() {
        val s = EventEditFormState.forDuplicate(
            source = source(
                startMillis = utc(2026, 5, 24, 0, 0),
                endMillis = utc(2026, 5, 26, 0, 0),
                allDay = true,
            ),
            instanceStartMillis = null,
            defaultDurationMinutes = 60,
            zone = ZoneOffset.UTC,
        )
        assertEquals(true, s.allDay)
        assertEquals(LocalDate.of(2026, 5, 24), s.startDate)
        assertEquals(LocalDate.of(2026, 5, 25), s.endDate)
    }
}
