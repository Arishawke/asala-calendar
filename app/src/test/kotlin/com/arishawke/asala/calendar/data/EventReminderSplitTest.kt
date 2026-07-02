/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EventReminderSplitTest {
    @Test
    fun `splits negatives from non-negatives and sorts the editable list`() {
        val split = splitReminderRows(listOf(30, -1, 0, 10))
        assertEquals(listOf(0, 10, 30), split.editable)
        assertEquals(listOf(-1), split.preserved)
    }

    @Test
    fun `no reminder rows yields two empty lists`() {
        val split = splitReminderRows(emptyList())
        assertEquals(emptyList<Int>(), split.editable)
        assertEquals(emptyList<Int>(), split.preserved)
    }

    // the editor collapses duplicates on save, so the reader keeps them verbatim.
    @Test
    fun `keeps duplicate editable offsets`() {
        val split = splitReminderRows(listOf(10, 10))
        assertEquals(listOf(10, 10), split.editable)
    }
}
