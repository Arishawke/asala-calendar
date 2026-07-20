/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.data

import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// the row identity setReminders writes: editable offsets as METHOD_ALERT, preserved
// rows verbatim, deduped by the full (minutes, method) row. these pin the corruption
// fix so a regression to distinctBy { minutes } or a hardcoded METHOD_ALERT fails.
class ReminderRowBuildTest {
    private val alert = CalendarContract.Reminders.METHOD_ALERT
    private val default = CalendarContract.Reminders.METHOD_DEFAULT
    private val email = CalendarContract.Reminders.METHOD_EMAIL

    @Test
    fun `editable offsets are written as alert rows`() {
        assertEquals(
            listOf(ReminderRow(10, alert), ReminderRow(30, alert)),
            buildReminderRows(listOf(10, 30), emptyList()),
        )
    }

    @Test
    fun `preserved rows keep their own method`() {
        val rows = buildReminderRows(listOf(10), listOf(ReminderRow(-1, default), ReminderRow(60, email)))
        assertTrue(rows.contains(ReminderRow(-1, default)))
        assertTrue(rows.contains(ReminderRow(60, email)))
    }

    // the regression this fix exists for: a synced email reminder and an authored
    // alert at the SAME minute must both survive. deduping by minutes alone (the old
    // bug's shape) would drop one.
    @Test
    fun `same minute different method are both kept`() {
        val rows = buildReminderRows(listOf(30), listOf(ReminderRow(30, email)))
        assertEquals(2, rows.size)
        assertTrue(rows.contains(ReminderRow(30, alert)))
        assertTrue(rows.contains(ReminderRow(30, email)))
    }

    // an exact duplicate row (same minute AND method) collapses to one.
    @Test
    fun `an exact duplicate row is deduped`() {
        val rows = buildReminderRows(listOf(10, 10), emptyList())
        assertEquals(listOf(ReminderRow(10, alert)), rows)
    }

    // a preserved alert row equal to an editable one collapses; distinct rows do not.
    @Test
    fun `a preserved row equal to an editable alert collapses`() {
        val rows = buildReminderRows(listOf(10), listOf(ReminderRow(10, alert)))
        assertEquals(listOf(ReminderRow(10, alert)), rows)
    }
}
