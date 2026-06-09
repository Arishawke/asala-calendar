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
import java.time.LocalTime

class RevealVisibilityTest {
    private val hourHeightPx = 72f // matches HourHeight default at 1x

    @Test fun target_px_is_hour_times_height() {
        assertEquals(648, revealTargetPx(LocalTime.of(9, 0), hourHeightPx))
    }

    @Test fun target_px_includes_minutes() {
        assertEquals(684, revealTargetPx(LocalTime.of(9, 30), hourHeightPx))
    }

    @Test fun midnight_is_zero_px() {
        assertEquals(0, revealTargetPx(LocalTime.MIDNIGHT, hourHeightPx))
    }

    @Test fun target_below_the_window_is_below() {
        // 9 PM (1512) below a 0..720 window
        assertEquals(RevealEdge.Below, revealEdge(1512, scrollPx = 0, viewportPx = 720, marginPx = 0))
    }

    @Test fun target_above_the_window_is_above() {
        // 3 AM (216) above a 7 AM (504) window
        assertEquals(RevealEdge.Above, revealEdge(216, scrollPx = 504, viewportPx = 720, marginPx = 0))
    }

    @Test fun target_inside_the_window_is_visible() {
        assertEquals(RevealEdge.Visible, revealEdge(540, scrollPx = 504, viewportPx = 720, marginPx = 0))
    }

    @Test fun margin_treats_a_sliver_as_above() {
        // target 6 px below the raw top, but inside the 20 px margin -> Above
        assertEquals(RevealEdge.Above, revealEdge(510, scrollPx = 504, viewportPx = 720, marginPx = 20))
    }

    @Test fun formats_12h_and_24h() {
        assertEquals("9:00 PM", formatRevealTime(LocalTime.of(21, 0), is24Hour = false))
        assertEquals("21:00", formatRevealTime(LocalTime.of(21, 0), is24Hour = true))
    }
}
