/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.ui.eventedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderListTest {
    @Test
    fun `add appends the timed default when one is set`() {
        assertEquals(30, reminderToAppend(30))
    }

    @Test
    fun `add appends the fallback when the default is none`() {
        assertEquals(FallbackReminderMinutes, reminderToAppend(null))
    }

    @Test
    fun `add is allowed below the cap and hidden at it`() {
        assertTrue(canAddReminder(0))
        assertTrue(canAddReminder(MaxReminders - 1))
        assertFalse(canAddReminder(MaxReminders))
        // a synced event with more rows than the cap still hides the add row only.
        assertFalse(canAddReminder(MaxReminders + 3))
    }
}
