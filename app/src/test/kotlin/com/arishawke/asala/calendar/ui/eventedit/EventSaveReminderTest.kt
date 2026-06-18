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

// The reminder-preservation contract on the edit path. setReminder deletes every
// reminder row for the event then inserts one, so EventSave must not call it on an
// in-place edit whose reminder is unchanged, or a multi-reminder event loses all
// but one. A new exception/split row has no reminders and must always be written.
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

    // Pre-fix: setReminder ran on every save, so a 3-reminder synced event kept
    // only 1 after editing its title. When the target is the original row and the
    // reminder is unchanged, the write must be skipped entirely.
    @Test
    fun `in-place edit with unchanged reminder does not rewrite reminders`() = runBlocking {
        var reminderCalled = false
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutesBefore = 10),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                loadedReminderMinutes = 10,
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ -> id },
                setReminder = { _, _ ->
                    reminderCalled = true
                    true
                },
            )
        assertEquals(SaveResult.Success(7L), result)
        assertTrue("setReminder must not run when the reminder is unchanged", !reminderCalled)
    }

    // When the user DID change the reminder, the write must run (the single-reminder
    // model collapses to one until multi-reminder editing ships, but a deliberate
    // reminder edit is expected to take effect).
    @Test
    fun `in-place edit with changed reminder writes the new value`() = runBlocking {
        var wrote: Int? = null
        var called = false
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutesBefore = 30),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                loadedReminderMinutes = 10,
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ -> id },
                setReminder = { _, m ->
                    called = true
                    wrote = m
                    true
                },
            )
        assertEquals(SaveResult.Success(7L), result)
        assertTrue("setReminder must run when the reminder changed", called)
        assertEquals(30, wrote)
    }

    // Removing the reminder (non-null loaded -> null) is a change, so the write
    // must run with null to clear the rows. This is the inverse of the unchanged-
    // null skip; the two are one boolean flip apart, so pin the asymmetry.
    @Test
    fun `in-place edit removing the reminder writes null`() = runBlocking {
        var called = false
        var wroteNull = false
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutesBefore = null),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                loadedReminderMinutes = 10,
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ -> id },
                setReminder = { _, m ->
                    called = true
                    wroteNull = m == null
                    true
                },
            )
        assertEquals(SaveResult.Success(7L), result)
        assertTrue("setReminder must run when the reminder is removed", called)
        assertTrue("setReminder must be called with null to clear the reminder", wroteNull)
    }

    // Editing one occurrence creates a NEW exception row that has no reminders, so
    // the unchanged-skip must NOT apply (effectiveId != editingEventId). The reminder
    // must still be written to the new row even when its value equals the parent's.
    @Test
    fun `ThisInstance writes the reminder to the new exception even when unchanged`() = runBlocking {
        val newExceptionId = 999L
        var reminderId: Long? = null
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutesBefore = 10),
                editingEventId = 7L,
                scope = RecurringEditScope.ThisInstance,
                instanceMillis = 1_700_000_000_000L,
                parentRrule = "FREQ=DAILY",
                loadedReminderMinutes = 10,
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
}
