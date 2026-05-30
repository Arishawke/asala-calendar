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

class EventSaveTest {
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

    // The form does not validate the calendar selection before save() is
    // called; bail at the save layer so the user sees a Failure rather
    // than an event silently created on calendar id 0.
    @Test
    fun `missing calendar id returns Failure without calling repos`() = runBlocking {
        var inserted = false
        var reminded = false
        val result =
            EventSave.attempt(
                form = form(selectedCalendarId = null),
                editingEventId = null,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                insertEvent = {
                    inserted = true
                    5L
                },
                updateEvent = { _, _, _, _, _, _ -> error("must not be called") },
                setReminder = { _, _ ->
                    reminded = true
                    true
                },
            )
        assertEquals(SaveResult.Failure, result)
        assertTrue("insertEvent must not be called", !inserted)
        assertTrue("setReminder must not be called", !reminded)
    }

    // Insert path happy case: the event id from insertEvent flows through
    // to SaveResult.Success and setReminder is invoked against it.
    @Test
    fun `create path succeeds when both writes succeed`() = runBlocking {
        var reminderEventId: Long? = null
        val result =
            EventSave.attempt(
                form = form(),
                editingEventId = null,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                insertEvent = { 42L },
                updateEvent = { _, _, _, _, _, _ -> error("must not be called on create path") },
                setReminder = { id, _ ->
                    reminderEventId = id
                    true
                },
            )
        assertEquals(SaveResult.Success(42L), result)
        assertEquals(42L, reminderEventId)
    }

    // Edit-this-instance: the repo creates a new exception event row whose
    // id differs from the parent series id. The reminder must attach to the
    // new exception (so it fires for that occurrence only) and must NOT
    // overwrite the parent's reminders. Pre-fix bug: setReminder was called
    // with the parent id, silently rewriting the parent series' reminder
    // and leaving the new exception with none.
    @Test
    fun `edit ThisInstance routes reminder to the new exception event id`() = runBlocking {
        val parentId = 7L
        val newExceptionId = 999L
        var reminderId: Long? = null
        val result =
            EventSave.attempt(
                form = form(),
                editingEventId = parentId,
                scope = RecurringEditScope.ThisInstance,
                instanceMillis = 1_700_000_000_000L,
                parentRrule = "FREQ=DAILY",
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { _, _, _, _, _, _ -> newExceptionId },
                setReminder = { id, _ ->
                    reminderId = id
                    true
                },
            )
        assertEquals(SaveResult.Success(newExceptionId), result)
        assertEquals(newExceptionId, reminderId)
    }

    // Edit-this-and-following: the repo splits the parent and creates a new
    // event row for the post-split occurrences. Same routing requirement as
    // ThisInstance: reminder attaches to the new row, not the original parent.
    @Test
    fun `edit ThisAndFollowing routes reminder to the new split event id`() = runBlocking {
        val parentId = 7L
        val newSplitId = 555L
        var reminderId: Long? = null
        val result =
            EventSave.attempt(
                form = form(),
                editingEventId = parentId,
                scope = RecurringEditScope.ThisAndFollowing,
                instanceMillis = 1_700_000_000_000L,
                parentRrule = "FREQ=DAILY",
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { _, _, _, _, _, _ -> newSplitId },
                setReminder = { id, _ ->
                    reminderId = id
                    true
                },
            )
        assertEquals(SaveResult.Success(newSplitId), result)
        assertEquals(newSplitId, reminderId)
    }

    // Partial failure on create: event row exists but setReminder
    // rejected the reminder write. Contract is Failure so the user sees
    // the banner; the event row is intentionally not rolled back because
    // an in-flight rollback could lose data on a transient provider hiccup
    // (better that the event survives and the reminder can be re-added).
    @Test
    fun `create path with reminder rejection returns Failure even after event inserted`() = runBlocking {
        var insertCalled = false
        val result =
            EventSave.attempt(
                form = form(),
                editingEventId = null,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                insertEvent = {
                    insertCalled = true
                    99L
                },
                updateEvent = { _, _, _, _, _, _ -> error("must not be called on create path") },
                setReminder = { _, _ -> false },
            )
        assertEquals(SaveResult.Failure, result)
        assertTrue("event must have been inserted before reminder rejection", insertCalled)
    }

    // Insert miss: provider rejected the event row. setReminder must not
    // run; we have no event id to attach the reminder to.
    @Test
    fun `create path with insert rejection skips reminder write`() = runBlocking {
        var reminderCalled = false
        val result =
            EventSave.attempt(
                form = form(),
                editingEventId = null,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                insertEvent = { null },
                updateEvent = { _, _, _, _, _, _ -> error("must not be called on create path") },
                setReminder = { _, _ ->
                    reminderCalled = true
                    true
                },
            )
        assertEquals(SaveResult.Failure, result)
        assertTrue("setReminder must not run without an event id", !reminderCalled)
    }

    // Edit path: updateEvent runs against the editing id, then setReminder
    // runs against the same id. Insert must not be called.
    @Test
    fun `edit path succeeds when both writes succeed`() = runBlocking {
        var updateId: Long? = null
        var reminderId: Long? = null
        val result =
            EventSave.attempt(
                form = form(),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ ->
                    updateId = id
                    id
                },
                setReminder = { id, _ ->
                    reminderId = id
                    true
                },
            )
        assertEquals(SaveResult.Success(7L), result)
        assertEquals(7L, updateId)
        assertEquals(7L, reminderId)
    }

    // Edit path reminder rejection: same partial-failure contract as the
    // create path. Event row was updated; reminder write rejected; user
    // sees Failure.
    @Test
    fun `edit path with reminder rejection returns Failure`() = runBlocking {
        val result =
            EventSave.attempt(
                form = form(),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ -> id },
                setReminder = { _, _ -> false },
            )
        assertEquals(SaveResult.Failure, result)
    }
}
