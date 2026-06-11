/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DragRescheduleTest {
    // 64 px per hour is the project's `HourHeight` default at 1x density;
    // any plausible value works here because the math is rate-relative.
    private val hourHeightPx = 64f

    @Test fun zero_pixel_delta_is_zero_minutes() {
        assertEquals(0, pxToMinutes(0f, hourHeightPx))
    }

    @Test fun one_full_hour_pixel_delta_is_sixty_minutes() {
        assertEquals(60, pxToMinutes(hourHeightPx, hourHeightPx))
    }

    @Test fun negative_pixel_delta_returns_negative_minutes() {
        assertEquals(-30, pxToMinutes(-hourHeightPx / 2f, hourHeightPx))
    }

    @Test fun fractional_pixels_round_to_nearest_minute() {
        // 64 px / hour => 64/60 = ~1.067 px per minute.
        // 1.5 px ≈ 1.4 minutes => rounds to 1
        assertEquals(1, pxToMinutes(1.5f, hourHeightPx))
    }

    @Test fun zero_hour_height_returns_zero_minutes_safely() {
        assertEquals(0, pxToMinutes(100f, 0f))
    }

    @Test fun snap_to_grid_rounds_to_nearest_fifteen() {
        assertEquals(0, snapToGrid(7))
        assertEquals(15, snapToGrid(8))
        assertEquals(15, snapToGrid(22))
        assertEquals(30, snapToGrid(23))
        assertEquals(-15, snapToGrid(-8))
        assertEquals(0, snapToGrid(-7))
    }

    @Test fun snap_to_grid_passes_through_exact_multiples() {
        assertEquals(0, snapToGrid(0))
        assertEquals(15, snapToGrid(15))
        assertEquals(60, snapToGrid(60))
        assertEquals(-30, snapToGrid(-30))
    }

    @Test fun snap_with_custom_grid_works() {
        // A 30-minute grid: small drags collapse to zero, mid drags
        // snap to 30.
        assertEquals(0, snapToGrid(10, snapMinutes = 30))
        assertEquals(30, snapToGrid(16, snapMinutes = 30))
        assertEquals(30, snapToGrid(44, snapMinutes = 30))
        assertEquals(60, snapToGrid(45, snapMinutes = 30))
    }

    @Test fun end_to_end_drag_eight_pixels_at_64_per_hour_snaps_to_15_min() {
        // 8 px / 64 (px/hr) * 60 = 7.5 minutes => rounds to 8 => snap to 15.
        val rawMin = pxToMinutes(8f, hourHeightPx)
        assertEquals(8, rawMin)
        assertEquals(15, snapToGrid(rawMin))
    }

    @Test fun end_to_end_drag_negative_64_pixels_snaps_to_negative_60_min() {
        // -64 px / 64 * 60 = -60 (exact) => snap to -60. Picks a value that
        // doesn't sit on a half-minute boundary so the round-half-up rule
        // for negatives stays out of this assertion.
        val rawMin = pxToMinutes(-hourHeightPx, hourHeightPx)
        assertEquals(-60, rawMin)
        assertEquals(-60, snapToGrid(rawMin))
    }

    // -- pxToDayDelta + clampDayDelta + applyDayAndMinuteDelta --

    @Test fun zero_x_delta_is_zero_day_delta() {
        assertEquals(0, pxToDayDelta(0f, 200f))
    }

    @Test fun one_full_column_x_delta_is_one_day() {
        assertEquals(1, pxToDayDelta(200f, 200f))
    }

    @Test fun negative_column_x_delta_returns_negative_day() {
        assertEquals(-2, pxToDayDelta(-400f, 200f))
    }

    @Test fun half_column_x_delta_rounds_to_one_day() {
        // 100/200 = 0.5 => rounds half up to 1
        assertEquals(1, pxToDayDelta(100f, 200f))
    }

    @Test fun zero_column_width_returns_zero_day_delta_safely() {
        assertEquals(0, pxToDayDelta(500f, 0f))
    }

    @Test fun clamp_day_delta_when_total_columns_is_one_returns_zero() {
        // Day view scenario: weekDayCount = 1, cross-day must be disabled.
        assertEquals(0, clampDayDelta(5, 0, 1))
        assertEquals(0, clampDayDelta(-3, 0, 1))
    }

    @Test fun clamp_day_delta_respects_current_column() {
        // From column 2 of 7, max forward = 4 (lands on col 6), max back = -2 (lands on col 0).
        assertEquals(4, clampDayDelta(10, 2, 7))
        assertEquals(-2, clampDayDelta(-10, 2, 7))
        assertEquals(3, clampDayDelta(3, 2, 7))
    }

    @Test fun apply_day_and_minute_delta_shifts_correctly() {
        val zone = ZoneId.of("UTC")
        val start = ZonedDateTime.of(LocalDate.of(2026, 5, 24), LocalTime.of(9, 0), zone)
            .toInstant().toEpochMilli()
        val result = applyDayAndMinuteDelta(start, zone, dayDelta = 2, minuteDelta = 30)
        val expected = ZonedDateTime.of(LocalDate.of(2026, 5, 26), LocalTime.of(9, 30), zone)
            .toInstant().toEpochMilli()
        assertEquals(expected, result)
    }

    @Test fun apply_day_and_minute_delta_handles_spring_forward() {
        // Los Angeles spring-forward 2026: 2026-03-08 02:00 -> 03:00 PDT.
        // Event at 09:00 on 2026-03-07. Drag to next day should land on
        // 09:00 on 2026-03-08 in local wall-clock time (not 10:00 if we
        // had naively added 24h in millis).
        val zone = ZoneId.of("America/Los_Angeles")
        val start = ZonedDateTime.of(LocalDate.of(2026, 3, 7), LocalTime.of(9, 0), zone)
            .toInstant().toEpochMilli()
        val result = applyDayAndMinuteDelta(start, zone, dayDelta = 1, minuteDelta = 0)
        val expected = ZonedDateTime.of(LocalDate.of(2026, 3, 8), LocalTime.of(9, 0), zone)
            .toInstant().toEpochMilli()
        assertEquals(expected, result)
    }

    @Test fun apply_day_and_minute_delta_handles_fall_back() {
        // Los Angeles fall-back 2026: 2026-11-01 02:00 PDT -> 01:00 PST.
        // Event at 09:00 on 2026-10-31. Drag to next day should land on
        // 09:00 on 2026-11-01 wall-clock (a 25-hour real-world gap).
        val zone = ZoneId.of("America/Los_Angeles")
        val start = ZonedDateTime.of(LocalDate.of(2026, 10, 31), LocalTime.of(9, 0), zone)
            .toInstant().toEpochMilli()
        val result = applyDayAndMinuteDelta(start, zone, dayDelta = 1, minuteDelta = 0)
        val expected = ZonedDateTime.of(LocalDate.of(2026, 11, 1), LocalTime.of(9, 0), zone)
            .toInstant().toEpochMilli()
        assertEquals(expected, result)
    }

    @Test fun apply_day_and_minute_delta_with_only_minute_shift_matches_simple_helper() {
        val zone = ZoneId.of("UTC")
        val start = 1_700_000_000_000L
        val combined = applyDayAndMinuteDelta(start, zone, dayDelta = 0, minuteDelta = 45)
        val simple = start + 45 * 60_000L
        assertEquals(simple, combined)
    }
}
