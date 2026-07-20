/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import android.provider.CalendarContract
import com.arishawke.asala.calendar.data.RecurringEditScope
import com.arishawke.asala.calendar.data.ReminderRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

// The reminder-preservation contract on the edit path. setReminders deletes every
// reminder row then reinserts the editable offsets as METHOD_ALERT plus any
// preserved rows verbatim, so EventSave must skip the write when the form's visible
// set equals the loaded set, and otherwise pass the visible set and the preserved
// rows (with their methods) intact.
class EventSaveReminderTest {
    private fun form(selectedCalendarId: Long? = 1L): EventEditFormState = EventEditFormState(
        selectedCalendarId = selectedCalendarId,
        title = "Lunch",
        description = "",
        location = "",
        startDate = LocalDate.of(2026, 6, 1),
        startTime = LocalTime.of(12, 0),
        endDate = LocalDate.of(2026, 6, 1),
        endTime = LocalTime.of(13, 0),
        allDay = false,
    )

    @Test
    fun `in-place edit with an unchanged set does not rewrite reminders`() = runBlocking {
        var reminderCalled = false
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutes = listOf(10, 30)),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                loadedReminderMinutes = listOf(30, 10),
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ -> id },
                setReminders = { _, _, _ ->
                    reminderCalled = true
                    true
                },
            )
        assertEquals(SaveResult.Success(7L), result)
        assertTrue("setReminders must not run when the set is unchanged", !reminderCalled)
    }

    @Test
    fun `unchanged comparison ignores order and duplicates`() = runBlocking {
        var called = false
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutes = listOf(30, 10, 10)),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                loadedReminderMinutes = listOf(10, 30),
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ -> id },
                setReminders = { _, _, _ ->
                    called = true
                    true
                },
            )
        assertEquals(SaveResult.Success(7L), result)
        assertTrue("a reorder or duplicate that collapses to the same set is not a change", !called)
    }

    @Test
    fun `in-place edit with a changed set writes the new list`() = runBlocking {
        var wrote: List<Int>? = null
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutes = listOf(10, 60)),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                loadedReminderMinutes = listOf(10),
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ -> id },
                setReminders = { _, editable, _ ->
                    wrote = editable
                    true
                },
            )
        assertEquals(SaveResult.Success(7L), result)
        assertEquals(listOf(10, 60), wrote)
    }

    @Test
    fun `clearing every reminder writes an empty list`() = runBlocking {
        var called = false
        var wrote: List<Int>? = null
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutes = emptyList()),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                loadedReminderMinutes = listOf(10),
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ -> id },
                setReminders = { _, editable, _ ->
                    called = true
                    wrote = editable
                    true
                },
            )
        assertEquals(SaveResult.Success(7L), result)
        assertTrue("setReminders must run when reminders are cleared", called)
        assertEquals(emptyList<Int>(), wrote)
    }

    // A synced event's non-authorable rows (the -1 default sentinel, a server-owned
    // email method) must be written back verbatim with their methods so a changed
    // visible set does not drop or clobber them.
    @Test
    fun `edit writes the visible set and preserves foreign rows with their methods`() = runBlocking {
        var wroteEditable: List<Int>? = null
        var wrotePreserved: List<ReminderRow>? = null
        val preserved =
            listOf(
                ReminderRow(-1, CalendarContract.Reminders.METHOD_DEFAULT),
                ReminderRow(30, CalendarContract.Reminders.METHOD_EMAIL),
            )
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutes = listOf(10, 60)),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                loadedReminderMinutes = listOf(10),
                preservedReminders = preserved,
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ -> id },
                setReminders = { _, editable, foreign ->
                    wroteEditable = editable
                    wrotePreserved = foreign
                    true
                },
            )
        assertEquals(SaveResult.Success(7L), result)
        assertEquals(listOf(10, 60), wroteEditable)
        assertEquals(preserved, wrotePreserved)
    }

    // A ThisInstance edit creates a NEW exception row with no reminders, so the
    // unchanged-skip must not apply and the set must be written to the new row.
    @Test
    fun `ThisInstance writes the set to the new exception even when unchanged`() = runBlocking {
        val newExceptionId = 999L
        var reminderId: Long? = null
        var wrote: List<Int>? = null
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutes = listOf(10)),
                editingEventId = 7L,
                scope = RecurringEditScope.ThisInstance,
                instanceMillis = 1_700_000_000_000L,
                parentRrule = "FREQ=DAILY",
                loadedReminderMinutes = listOf(10),
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { _, _, _, _, _, _ -> newExceptionId },
                setReminders = { id, editable, _ ->
                    reminderId = id
                    wrote = editable
                    true
                },
            )
        assertEquals(SaveResult.Success(newExceptionId), result)
        assertEquals(newExceptionId, reminderId)
        assertEquals(listOf(10), wrote)
    }

    @Test
    fun `new event writes the form reminder list`() = runBlocking {
        var wrote: List<Int>? = null
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutes = listOf(5, 15)),
                editingEventId = null,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                insertEvent = { 42L },
                updateEvent = { _, _, _, _, _, _ -> error("must not be called on new path") },
                setReminders = { _, editable, _ ->
                    wrote = editable
                    true
                },
            )
        assertEquals(SaveResult.Success(42L), result)
        assertEquals(listOf(5, 15), wrote)
    }

    // duplicate picker selections are reachable from the list UI (two rows can
    // pick the same offset); the write must dedupe rather than insert twin rows.
    @Test
    fun `a duplicated offset writes once`() = runBlocking {
        var wrote: List<Int>? = null
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutes = listOf(10, 10)),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                loadedReminderMinutes = emptyList(),
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ -> id },
                setReminders = { _, editable, _ ->
                    wrote = editable
                    true
                },
            )
        assertEquals(SaveResult.Success(7L), result)
        assertEquals(listOf(10), wrote)
    }
}
