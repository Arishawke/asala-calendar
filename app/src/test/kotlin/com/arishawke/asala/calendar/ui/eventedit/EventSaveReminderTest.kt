/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import com.arishawke.asala.calendar.data.RecurringEditScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

// The reminder-preservation contract on the edit path. setReminders deletes every
// reminder row then inserts one per distinct value, so EventSave must skip the
// write when the form's non-negative set equals the loaded set, and otherwise
// write exactly the visible set plus any preserved negative rows.
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
                setReminders = { _, _ ->
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
                setReminders = { _, _ ->
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
                setReminders = { _, m ->
                    wrote = m
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
                setReminders = { _, m ->
                    called = true
                    wrote = m
                    true
                },
            )
        assertEquals(SaveResult.Success(7L), result)
        assertTrue("setReminders must run when reminders are cleared", called)
        assertEquals(emptyList<Int>(), wrote)
    }

    // A synced event's negative-offset rows are not authorable; a changed visible
    // set must write them back verbatim so the edit does not drop them.
    @Test
    fun `edit writes the visible set plus preserved negative rows`() = runBlocking {
        var wrote: List<Int>? = null
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutes = listOf(10, 60)),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                loadedReminderMinutes = listOf(10),
                preservedReminderMinutes = listOf(-1),
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ -> id },
                setReminders = { _, m ->
                    wrote = m
                    true
                },
            )
        assertEquals(SaveResult.Success(7L), result)
        assertEquals(listOf(10, 60, -1), wrote)
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
                setReminders = { id, m ->
                    reminderId = id
                    wrote = m
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
                setReminders = { _, m ->
                    wrote = m
                    true
                },
            )
        assertEquals(SaveResult.Success(42L), result)
        assertEquals(listOf(5, 15), wrote)
    }
}
