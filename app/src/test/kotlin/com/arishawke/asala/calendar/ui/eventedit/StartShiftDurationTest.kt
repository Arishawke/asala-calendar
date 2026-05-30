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

/**
 * Pins the duration-preservation invariant of EventEditFormState.withStartDate
 * and withStartTime. EventForm.kt's onDateChange / onTimeChange call these
 * directly, so a regression in production code now fails this test.
 */
class StartShiftDurationTest {
    private fun baseState(
        startDate: LocalDate = LocalDate.of(2026, 5, 22),
        startTime: LocalTime = LocalTime.of(14, 0),
        endDate: LocalDate = LocalDate.of(2026, 5, 22),
        endTime: LocalTime = LocalTime.of(15, 0),
        allDay: Boolean = false,
    ) = EventEditFormState(
        startDate = startDate,
        startTime = startTime,
        endDate = endDate,
        endTime = endTime,
        allDay = allDay,
    )

    @Test fun timed_start_moves_forward_preserves_duration() {
        // 14:00 - 15:00 (60 min); start moves to 16:00 => end should be 17:00
        val after = baseState().withStartTime(LocalTime.of(16, 0))
        assertEquals(LocalTime.of(16, 0), after.startTime)
        assertEquals(LocalTime.of(17, 0), after.endTime)
        assertEquals(LocalDate.of(2026, 5, 22), after.endDate)
    }

    @Test fun timed_start_moves_backward_preserves_duration() {
        val after = baseState().withStartTime(LocalTime.of(10, 0))
        assertEquals(LocalTime.of(10, 0), after.startTime)
        assertEquals(LocalTime.of(11, 0), after.endTime)
    }

    @Test fun time_shift_past_midnight_rolls_end_date_forward() {
        // 23:00 - 23:30; start moves to 23:45 (+45 min delta) => end 00:15 next day
        val state =
            baseState(
                startTime = LocalTime.of(23, 0),
                endTime = LocalTime.of(23, 30),
            )
        val after = state.withStartTime(LocalTime.of(23, 45))
        assertEquals(LocalTime.of(23, 45), after.startTime)
        assertEquals(LocalDate.of(2026, 5, 23), after.endDate)
        assertEquals(LocalTime.of(0, 15), after.endTime)
    }

    @Test fun all_day_start_date_moves_forward_shifts_end_date() {
        // Mon - Wed (3-day span); start moves to Wed => end moves to Fri
        val state =
            baseState(
                startDate = LocalDate.of(2026, 5, 18),
                endDate = LocalDate.of(2026, 5, 20),
                allDay = true,
            )
        val after = state.withStartDate(LocalDate.of(2026, 5, 20))
        assertEquals(LocalDate.of(2026, 5, 20), after.startDate)
        assertEquals(LocalDate.of(2026, 5, 22), after.endDate)
    }

    @Test fun all_day_start_date_moves_backward_shifts_end_date() {
        val state =
            baseState(
                startDate = LocalDate.of(2026, 5, 18),
                endDate = LocalDate.of(2026, 5, 18),
                allDay = true,
            )
        val after = state.withStartDate(LocalDate.of(2026, 5, 16))
        assertEquals(LocalDate.of(2026, 5, 16), after.startDate)
        assertEquals(LocalDate.of(2026, 5, 16), after.endDate)
    }

    @Test fun timed_start_date_moves_forward_shifts_end_date() {
        // Same-day 14:00 - 15:00; start moves to next day => end also next day, 15:00
        val after = baseState().withStartDate(LocalDate.of(2026, 5, 23))
        assertEquals(LocalDate.of(2026, 5, 23), after.startDate)
        assertEquals(LocalDate.of(2026, 5, 23), after.endDate)
        assertEquals(LocalTime.of(15, 0), after.endTime)
    }
}
