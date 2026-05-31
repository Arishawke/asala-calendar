/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import com.arishawke.asala.calendar.ui.multidaybars.WeekSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.YearMonth

class SelfContainedMonthGridTest {

    // The run-on the self-contained surface removes is the empty trailing
    // week: a 5-week month must not reserve a 6th all-filler row, but a
    // month that genuinely spills into a 6th week must keep it.

    @Test fun five_week_month_drops_the_empty_sixth_week() {
        // March 2026 starts on a Sunday; week-start Sunday => no leading
        // filler, 31 days land in 5 weeks.
        val days = buildMonthGrid(YearMonth.of(2026, 3), DayOfWeek.SUNDAY)
        assertEquals(5, weeksWithMonthDays(days))
    }

    @Test fun month_spilling_into_a_sixth_week_keeps_it() {
        // Same month, week-start Monday => 6 days of leading filler push
        // the 31st into a 6th week, which must still render.
        val days = buildMonthGrid(YearMonth.of(2026, 3), DayOfWeek.MONDAY)
        assertEquals(6, weeksWithMonthDays(days))
    }

    @Test fun short_month_uses_only_four_weeks() {
        // February 2026: 28 days starting on a Sunday fit exactly 4 weeks.
        val days = buildMonthGrid(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)
        assertEquals(4, weeksWithMonthDays(days))
    }

    // Bars must stop at the month edge rather than spanning the blanked
    // adjacent-month cells, and must show a squared cut where they were
    // clipped so the user reads "continues beyond this month".

    @Test fun bar_clipped_to_leading_in_month_columns_marks_cut_left() {
        val seg = segment(startCol = 0, endCol = 6)
        val clipped = clipSegmentsToColumns(listOf(seg), firstInMonthCol = 2, lastInMonthCol = 6)
        assertEquals(1, clipped.size)
        assertEquals(2, clipped[0].startCol)
        assertEquals(6, clipped[0].endCol)
        assertTrue("left edge was cut so continuation must show", clipped[0].isContinuedLeft)
        assertFalse(clipped[0].isContinuedRight)
    }

    @Test fun bar_entirely_in_filler_is_dropped() {
        val seg = segment(startCol = 5, endCol = 6)
        val clipped = clipSegmentsToColumns(listOf(seg), firstInMonthCol = 0, lastInMonthCol = 3)
        assertTrue("a bar living only in adjacent-month cells should not render", clipped.isEmpty())
    }

    @Test fun bar_fully_in_month_is_unchanged() {
        val seg = segment(startCol = 1, endCol = 2)
        val clipped = clipSegmentsToColumns(listOf(seg), firstInMonthCol = 0, lastInMonthCol = 6)
        assertEquals(1, clipped[0].startCol)
        assertEquals(2, clipped[0].endCol)
        assertFalse(clipped[0].isContinuedLeft)
        assertFalse(clipped[0].isContinuedRight)
    }

    @Test fun week_with_no_month_days_clips_to_nothing() {
        val seg = segment(startCol = 0, endCol = 6)
        val clipped = clipSegmentsToColumns(listOf(seg), firstInMonthCol = -1, lastInMonthCol = -1)
        assertTrue(clipped.isEmpty())
    }

    private fun segment(startCol: Int, endCol: Int) = WeekSegment(
        eventId = 1L,
        title = "e",
        color = 0,
        startCol = startCol,
        endCol = endCol,
        isContinuedLeft = false,
        isContinuedRight = false,
    )
}
