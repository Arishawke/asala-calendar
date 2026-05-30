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

// Pins the LazyColumn entry index <-> month index mapping for the
// continuous-scroll Month surface. The first shipping cut of v0.13
// regressed here: each month occupies a stickyHeader + grid pair
// (two LazyColumn entries), so passing the month index directly to
// rememberLazyListState/animateScrollToItem landed at half the
// expected position (today -> November 2023 instead of today). If
// LazyEntriesPerMonth ever drops back to 1 or the helpers stop
// applying it, this test fails.
class ContinuousMonthIndexTest {
    @Test fun month_index_60_maps_to_lazy_entry_120_with_header_plus_grid_pairs() {
        assertEquals(120, monthIndexToLazyIndex(60))
    }

    @Test fun lazy_entry_120_maps_back_to_month_index_60() {
        assertEquals(60, lazyIndexToMonthIndex(120))
    }

    @Test fun roundtrip_holds_for_every_month_in_range() {
        for (m in 0..120) {
            val lazyIdx = monthIndexToLazyIndex(m)
            assertEquals("monthIdx=$m did not round-trip", m, lazyIndexToMonthIndex(lazyIdx))
        }
    }

    @Test fun grid_entry_at_odd_lazy_index_still_maps_to_its_month() {
        // Header for month 30 is at lazy 60; its grid is at lazy 61.
        // Either entry should resolve to month 30 when the center
        // detector picks it as the viewport center.
        assertEquals(30, lazyIndexToMonthIndex(60))
        assertEquals(30, lazyIndexToMonthIndex(61))
    }

    @Test fun lazy_entries_per_month_is_two() {
        // The stickyHeader + item pair contract is load-bearing. If a
        // future refactor combines them into one entry, every scroll
        // helper above needs to drop the 2x conversion.
        assertEquals(2, LazyEntriesPerMonth)
    }
}
