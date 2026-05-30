/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthRangeWindowTest {
    @Test fun radius_1_matches_paged_window() {
        val ym = YearMonth.of(2026, 5)
        val (start, endExclusive) = MonthViewModel.monthFetchWindow(ym, radius = 1)
        assertEquals(LocalDate.of(2026, 4, 1), start)
        assertEquals(LocalDate.of(2026, 7, 1), endExclusive)
    }

    @Test fun radius_6_returns_13_month_window() {
        val ym = YearMonth.of(2026, 5)
        val (start, endExclusive) = MonthViewModel.monthFetchWindow(ym, radius = 6)
        assertEquals(LocalDate.of(2025, 11, 1), start)
        assertEquals(LocalDate.of(2026, 12, 1), endExclusive)
    }

    @Test fun radius_0_returns_single_month() {
        val ym = YearMonth.of(2026, 5)
        val (start, endExclusive) = MonthViewModel.monthFetchWindow(ym, radius = 0)
        assertEquals(LocalDate.of(2026, 5, 1), start)
        assertEquals(LocalDate.of(2026, 6, 1), endExclusive)
    }

    @Test fun continuous_radius_stays_wide_enough_to_cover_pre_composed_items() {
        // A LazyColumn fling pre-composes multiple items off-screen. If
        // this radius drops back to paged's 1, the user sees blank cells
        // streaming past. The exact ceiling is a tuning knob, but the
        // floor is "wider than paged".
        assertTrue(
            "ContinuousMonthWindowRadius regressed below safe floor: $ContinuousMonthWindowRadius",
            ContinuousMonthWindowRadius >= MinSafeContinuousRadius,
        )
    }

    private companion object {
        const val MinSafeContinuousRadius = 5
    }
}
