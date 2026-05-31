/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.year

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.Year

class YearFetchWindowTest {
    // The window must cover the whole visible year plus a one-year buffer on
    // each side so the mini-month grids (which spill lead/trail days into
    // adjacent months) render dots without reloading on every scroll.
    @Test
    fun `radius 1 spans previous year start to year-after-next start`() {
        val (start, endExclusive) = YearViewModel.yearFetchWindow(Year.of(2026), radiusYears = 1)
        assertEquals(LocalDate.of(2025, 1, 1), start)
        assertEquals(LocalDate.of(2028, 1, 1), endExclusive)
    }

    @Test
    fun `radius 0 spans exactly the one year, end-exclusive`() {
        val (start, endExclusive) = YearViewModel.yearFetchWindow(Year.of(2030), radiusYears = 0)
        assertEquals(LocalDate.of(2030, 1, 1), start)
        assertEquals(LocalDate.of(2031, 1, 1), endExclusive)
    }
}
