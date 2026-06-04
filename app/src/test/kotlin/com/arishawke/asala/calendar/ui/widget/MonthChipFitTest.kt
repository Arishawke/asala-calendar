package com.arishawke.asala.calendar.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class MonthChipFitTest {
    @Test
    fun `a short widget shows a single chip per cell`() {
        // ~220dp tall over 6 rows leaves little room: one chip plus a "+N".
        assertEquals(1, maxChipsPerCell(heightDp = 220f, rows = 6))
    }

    @Test
    fun `a default-size widget shows two chips`() {
        assertEquals(2, maxChipsPerCell(heightDp = 280f, rows = 5))
    }

    @Test
    fun `a tall widget shows the full cap`() {
        assertEquals(MonthGridBuilder.MAX_CHIPS, maxChipsPerCell(heightDp = 520f, rows = 4))
    }

    @Test
    fun `it never drops below one chip`() {
        assertEquals(1, maxChipsPerCell(heightDp = 40f, rows = 6))
    }
}
