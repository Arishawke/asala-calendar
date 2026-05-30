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
import org.junit.Test

class MonthCenterDetectionTest {
    @Test fun picks_the_item_whose_center_is_closest_to_viewport_center() {
        val items = listOf(
            VisibleItem(index = 0, offset = -200, size = 600),
            VisibleItem(index = 1, offset = 400, size = 600),
            VisibleItem(index = 2, offset = 1000, size = 600),
        )
        // item 0 center=100 (dist 400), item 1 center=700 (dist 200),
        // item 2 center=1300 (dist 800) -> item 1 wins.
        val center = ContinuousMonthCenter.pick(items, viewportCenter = 500, fallback = 99)
        assertEquals(1, center)
    }

    @Test fun empty_visible_items_returns_fallback() {
        val center = ContinuousMonthCenter.pick(emptyList(), viewportCenter = 0, fallback = 42)
        assertEquals(42, center)
    }

    @Test fun tie_breaks_to_first_in_list() {
        val items = listOf(
            VisibleItem(index = 5, offset = 0, size = 100),
            VisibleItem(index = 6, offset = 100, size = 100),
        )
        // viewportCenter = 100. item 5 center = 50 (dist 50). item 6 center
        // = 150 (dist 50). minByOrNull breaks ties to the first match.
        val center = ContinuousMonthCenter.pick(items, viewportCenter = 100, fallback = 99)
        assertEquals(5, center)
    }
}
