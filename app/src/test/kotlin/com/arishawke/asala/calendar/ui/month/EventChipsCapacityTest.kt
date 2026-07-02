/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

// tolerance for float dp comparisons; the formula itself must stay exact.
private const val DpTolerance = 0.001f

class EventChipsCapacityTest {
    // ~18dp chip row (bodySmall line height + 2x1dp padding) at factor 1.0.
    @Test
    fun `default scale fits two chip rows in a 50dp cell`() {
        assertEquals(2, chipCapacity(maxHeightDp = 50f, chipRowHeightDp = 18f, maxChips = MaxEventChipsPerCell))
    }

    // 85%: everything shrinks, so the same cell gains headroom for a third row.
    @Test
    fun `shrunk scale gains headroom in the same cell`() {
        assertEquals(3, chipCapacity(maxHeightDp = 50f, chipRowHeightDp = 15.3f, maxChips = MaxEventChipsPerCell))
    }

    // 150%: floor, not round, so a row that does not fully fit never overpaints.
    @Test
    fun `enlarged scale never overpaints a row that does not fully fit`() {
        assertEquals(1, chipCapacity(maxHeightDp = 50f, chipRowHeightDp = 27f, maxChips = MaxEventChipsPerCell))
    }

    // the +N reservation cap still holds even when the real height fits more,
    // so the roadmap's variable-chip-height design space stays open.
    @Test
    fun `capacity never exceeds the configured max even with generous headroom`() {
        assertEquals(
            MaxEventChipsPerCell,
            chipCapacity(maxHeightDp = 96f, chipRowHeightDp = 15.3f, maxChips = MaxEventChipsPerCell),
        )
    }

    // pins the real toDp() fontScale multiply, not just the padding add: a
    // dropped fontScale term would still pass at 1f but fail here. 1.5f
    // lands on Android's non-linear large-font curve (16sp interpolates
    // to 23dp, not a naive 16*1.5=24dp), so 25f is the real toDp() output.
    @Test
    fun `chip row height scales bodySmall line height by fontScale before padding`() {
        assertEquals(18f, Density(density = 1f, fontScale = 1f).chipRowHeight(16.sp).value, DpTolerance)
        assertEquals(15.6f, Density(density = 1f, fontScale = 0.85f).chipRowHeight(16.sp).value, DpTolerance)
        assertEquals(25f, Density(density = 1f, fontScale = 1.5f).chipRowHeight(16.sp).value, DpTolerance)
    }
}
