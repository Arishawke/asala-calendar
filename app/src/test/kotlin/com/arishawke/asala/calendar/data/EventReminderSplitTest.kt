/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.data

import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Test

class EventReminderSplitTest {
    private val alert = CalendarContract.Reminders.METHOD_ALERT
    private val default = CalendarContract.Reminders.METHOD_DEFAULT
    private val email = CalendarContract.Reminders.METHOD_EMAIL

    private fun rows(vararg pairs: Pair<Int, Int>): List<ReminderRow> = pairs.map { ReminderRow(it.first, it.second) }

    @Test
    fun `splits negatives from non-negatives and sorts the editable list`() {
        val split = splitReminderRows(rows(30 to alert, -1 to alert, 0 to alert, 10 to alert))
        assertEquals(listOf(0, 10, 30), split.editable)
        assertEquals(rows(-1 to alert), split.preserved)
    }

    @Test
    fun `no reminder rows yields two empty lists`() {
        val split = splitReminderRows(emptyList())
        assertEquals(emptyList<Int>(), split.editable)
        assertEquals(emptyList<ReminderRow>(), split.preserved)
    }

    // the editor collapses duplicates on save, so the reader keeps them verbatim.
    @Test
    fun `keeps duplicate editable offsets`() {
        val split = splitReminderRows(rows(10 to alert, 10 to alert))
        assertEquals(listOf(10, 10), split.editable)
    }

    // a synced calendar's email reminder is server-owned; it must not surface as an
    // editable alert (which a save would rewrite to METHOD_ALERT), but be preserved.
    @Test
    fun `a server-owned method row is preserved not editable`() {
        val split = splitReminderRows(rows(10 to alert, 30 to email))
        assertEquals(listOf(10), split.editable)
        assertEquals(rows(30 to email), split.preserved)
    }

    // the -1 default sentinel is preserved with its own method, whatever it is.
    @Test
    fun `the default sentinel is preserved with its method`() {
        val split = splitReminderRows(rows(-1 to default, 15 to alert))
        assertEquals(listOf(15), split.editable)
        assertEquals(rows(-1 to default), split.preserved)
    }

    // a non-negative METHOD_DEFAULT row is not something the app authored, so it is
    // preserved rather than shown as an editable alert.
    @Test
    fun `a non-negative default-method row is preserved`() {
        val split = splitReminderRows(rows(20 to default))
        assertEquals(emptyList<Int>(), split.editable)
        assertEquals(rows(20 to default), split.preserved)
    }
}
