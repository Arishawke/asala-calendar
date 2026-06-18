/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.multidaybars

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiDayOverflowTest {
    private fun seg(id: Long, lane: Int): WeekSegment = WeekSegment(
        eventId = id,
        title = "e$id",
        color = 0,
        startCol = 0,
        endCol = 6,
        isContinuedLeft = false,
        isContinuedRight = false,
        lane = lane,
    )

    @Test fun `segments within the cap have no overflow`() {
        val segments = listOf(seg(1L, 0), seg(2L, 1), seg(3L, 2), seg(4L, 3))
        assertEquals(emptySet<Long>(), overflowEventIds(segments, maxLanes = 4))
    }

    @Test fun `segments at or beyond the cap are overflow`() {
        // lanes 0-3 fit; lanes 4 and 5 are dropped by the row and must surface.
        val segments = listOf(seg(1L, 0), seg(2L, 3), seg(3L, 4), seg(4L, 5))
        assertEquals(setOf(3L, 4L), overflowEventIds(segments, maxLanes = 4))
    }

    @Test fun `empty input has no overflow`() {
        assertEquals(emptySet<Long>(), overflowEventIds(emptyList(), maxLanes = 4))
    }
}
