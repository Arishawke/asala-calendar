/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.threeday

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ThreeDayPageMathTest {
    private val anchor: LocalDate = LocalDate.of(2026, 3, 2)
    private val center = 20

    @Test fun `center page starts at the anchor`() {
        assertEquals(anchor, pageStart(anchor, center, center))
    }

    @Test fun `next page is anchor plus three days`() {
        assertEquals(anchor.plusDays(3), pageStart(anchor, center + 1, center))
    }

    @Test fun `previous page is anchor minus three days`() {
        assertEquals(anchor.minusDays(3), pageStart(anchor, center - 1, center))
    }

    @Test fun `consecutive pages tile contiguously without overlap`() {
        val p0 = pageStart(anchor, center, center)
        val p1 = pageStart(anchor, center + 1, center)
        // page center covers [p0, p0+3); the next page begins exactly at p0+3.
        assertEquals(p0.plusDays(3), p1)
    }

    @Test fun `pageForDate inverts pageStart at the anchor`() {
        assertEquals(center, pageForDate(anchor, anchor, center))
    }

    @Test fun `every day of a page maps to that page`() {
        assertEquals(center, pageForDate(anchor, anchor, center))
        assertEquals(center, pageForDate(anchor, anchor.plusDays(1), center))
        assertEquals(center, pageForDate(anchor, anchor.plusDays(2), center))
        // The day after rolls into the next page.
        assertEquals(center + 1, pageForDate(anchor, anchor.plusDays(3), center))
    }

    @Test fun `pageForDate floors negative offsets to the earlier page`() {
        // A date before the anchor belongs to an earlier page. Without
        // Math.floorDiv these would round toward center and land wrong.
        assertEquals(center - 1, pageForDate(anchor, anchor.minusDays(1), center))
        assertEquals(center - 1, pageForDate(anchor, anchor.minusDays(3), center))
        assertEquals(center - 2, pageForDate(anchor, anchor.minusDays(4), center))
    }

    @Test fun `the page pageForDate names always contains the date`() {
        // Round-trip invariant across a spread of offsets, both signs.
        for (offset in -10..10) {
            val date = anchor.plusDays(offset.toLong())
            val page = pageForDate(anchor, date, center)
            val start = pageStart(anchor, page, center)
            assertTrue(
                "date=$date page=$page start=$start",
                !date.isBefore(start) && date.isBefore(start.plusDays(3)),
            )
        }
    }
}
