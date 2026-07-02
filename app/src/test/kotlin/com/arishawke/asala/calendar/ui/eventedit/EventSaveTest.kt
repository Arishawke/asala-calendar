/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import com.arishawke.asala.calendar.data.RecurrenceFrequency
import com.arishawke.asala.calendar.data.RecurringEditScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

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
                setReminders = { _, _ ->
                    reminded = true
                    true
                },
            )
        assertEquals(SaveResult.Failure, result)
        assertTrue("insertEvent must not be called", !inserted)
        assertTrue("setReminder must not be called", !reminded)
    }

    // A recurrence whose UNTIL date precedes the event start expands to zero
    // occurrences. The provider accepts it, so the event would save "successfully"
    // and silently vanish. Reject it at the save layer (the date picker also
    // blocks it) so the user sees the failure banner instead.
    @Test
    fun `recurrence until before the start date returns Failure without inserting`() = runBlocking {
        var inserted = false
        val result =
            EventSave.attempt(
                form = form().copy(
                    recurrenceFrequency = RecurrenceFrequency.Weekly,
                    recurrenceUntilDate = LocalDate.of(2026, 5, 31),
                ),
                editingEventId = null,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                insertEvent = {
                    inserted = true
                    1L
                },
                updateEvent = { _, _, _, _, _, _ -> error("must not be called") },
                setReminders = { _, _ -> true },
            )
        assertEquals(SaveResult.Failure, result)
        assertTrue("must not insert a zero-occurrence recurrence", !inserted)
    }

    // UNTIL equal to the start date is valid: the series keeps its first
    // occurrence. The guard uses strict isBefore precisely to allow this; a
    // refactor to <= would silently reject a legitimate single-occurrence series.
    @Test
    fun `recurrence until equal to the start date still saves`() = runBlocking {
        var inserted = false
        val result =
            EventSave.attempt(
                form = form().copy(
                    recurrenceFrequency = RecurrenceFrequency.Weekly,
                    recurrenceUntilDate = LocalDate.of(2026, 6, 1),
                ),
                editingEventId = null,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                insertEvent = {
                    inserted = true
                    42L
                },
                updateEvent = { _, _, _, _, _, _ -> error("must not be called") },
                setReminders = { _, _ -> true },
            )
        assertEquals(SaveResult.Success(42L), result)
        assertTrue("a same-day UNTIL must still insert", inserted)
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
                setReminders = { id, _ ->
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
                setReminders = { id, _ ->
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
                setReminders = { id, _ ->
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
                setReminders = { _, _ -> false },
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
                setReminders = { _, _ ->
                    reminderCalled = true
                    true
                },
            )
        assertEquals(SaveResult.Failure, result)
        assertTrue("setReminder must not run without an event id", !reminderCalled)
    }

    // Edit path: updateEvent runs against the editing id, then setReminder
    // runs against the same id. Insert must not be called. The reminder is
    // changed (30 vs loaded 10) so the write runs; an unchanged reminder is
    // intentionally skipped (see the unchanged-reminder test below).
    @Test
    fun `edit path succeeds when both writes succeed`() = runBlocking {
        var updateId: Long? = null
        var reminderId: Long? = null
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutes = listOf(30)),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                loadedReminderMinutes = listOf(10),
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ ->
                    updateId = id
                    id
                },
                setReminders = { id, _ ->
                    reminderId = id
                    true
                },
            )
        assertEquals(SaveResult.Success(7L), result)
        assertEquals(7L, updateId)
        assertEquals(7L, reminderId)
    }

    // Editing the whole series without touching recurrence must keep the loaded
    // rule verbatim. build() only models FREQ/INTERVAL/UNTIL/COUNT, so rebuilding
    // would drop the BYDAY and widen the sub-day UNTIL to ...235959Z, which can
    // resurrect an occurrence a prior "this and following" split moved away.
    @Test
    fun `AllEvents edit keeps the original rule when recurrence is untouched`() = runBlocking {
        var savedRrule: String? = "sentinel"
        val original = "FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20260301T080000Z"
        val recurringForm = form().copy(
            // start precedes the UNTIL so the edited series is well-formed
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 1),
            recurrenceFrequency = RecurrenceFrequency.Weekly,
            recurrenceInterval = 1,
            recurrenceUntilDate = LocalDate.of(2026, 3, 1),
            recurrenceCount = null,
        )
        EventSave.attempt(
            form = recurringForm,
            editingEventId = 7L,
            scope = RecurringEditScope.AllEvents,
            instanceMillis = null,
            parentRrule = original,
            loadedTimezone = "UTC",
            insertEvent = { error("must not be called on edit path") },
            updateEvent = { _, draft, _, _, _, _ ->
                savedRrule = draft.rrule
                7L
            },
            setReminders = { _, _ -> true },
        )
        assertEquals(original, savedRrule)
    }

    // Moving the end date is a real recurrence change, so the rule is rebuilt
    // (and the editor's day-granular UNTIL correctly closes the day).
    @Test
    fun `AllEvents edit rebuilds the rule when the end date changed`() = runBlocking {
        var savedRrule: String? = null
        val recurringForm = form().copy(
            // start precedes the UNTIL so the edited series is well-formed
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 1),
            recurrenceFrequency = RecurrenceFrequency.Weekly,
            recurrenceInterval = 1,
            recurrenceUntilDate = LocalDate.of(2026, 3, 8),
            recurrenceCount = null,
        )
        EventSave.attempt(
            form = recurringForm,
            editingEventId = 7L,
            scope = RecurringEditScope.AllEvents,
            instanceMillis = null,
            parentRrule = "FREQ=WEEKLY;UNTIL=20260301T080000Z",
            loadedTimezone = "UTC",
            insertEvent = { error("must not be called on edit path") },
            updateEvent = { _, draft, _, _, _, _ ->
                savedRrule = draft.rrule
                7L
            },
            setReminders = { _, _ -> true },
        )
        assertEquals("FREQ=WEEKLY;UNTIL=20260308T235959Z", savedRrule)
    }

    // F7: editing a series authored in another zone must keep its
    // EVENT_TIMEZONE, not rewrite it to the device zone (which would shift the
    // intended-zone occurrences). Tz is threaded like loadedStatus/availability.
    @Test
    fun `edit preserves the loaded event timezone instead of the device zone`() = runBlocking {
        var savedTz: String? = null
        EventSave.attempt(
            form = form(),
            editingEventId = 7L,
            scope = RecurringEditScope.AllEvents,
            instanceMillis = null,
            parentRrule = null,
            loadedTimezone = "America/New_York",
            insertEvent = { error("must not be called on edit path") },
            updateEvent = { _, draft, _, _, _, _ ->
                savedTz = draft.eventTimezone
                7L
            },
            setReminders = { _, _ -> true },
        )
        assertEquals("America/New_York", savedTz)
    }

    // New events have no loaded zone, so they default to the device zone.
    @Test
    fun `new event uses the device timezone`() = runBlocking {
        var savedTz: String? = null
        EventSave.attempt(
            form = form(),
            editingEventId = null,
            scope = RecurringEditScope.AllEvents,
            instanceMillis = null,
            parentRrule = null,
            insertEvent = { draft ->
                savedTz = draft.eventTimezone
                5L
            },
            updateEvent = { _, _, _, _, _, _ -> error("must not be called on create path") },
            setReminders = { _, _ -> true },
        )
        assertEquals(TimeZone.getDefault().id, savedTz)
    }

    // F6 wiring: a rebuilt rule on a non-UTC series closes the day at the event
    // zone's end-of-day, expressed in UTC (not a blanket T235959Z).
    @Test
    fun `edit rebuilds non-utc series UNTIL at the event zone end of day`() = runBlocking {
        var savedRrule: String? = null
        val recurringForm = form().copy(
            recurrenceFrequency = RecurrenceFrequency.Daily,
            recurrenceInterval = 1,
            recurrenceUntilDate = LocalDate.of(2026, 12, 31),
            recurrenceCount = null,
        )
        EventSave.attempt(
            form = recurringForm,
            editingEventId = 7L,
            scope = RecurringEditScope.AllEvents,
            instanceMillis = null,
            // a different UNTIL than the form's date forces a rebuild.
            parentRrule = "FREQ=DAILY;UNTIL=20260101T045959Z",
            loadedTimezone = "America/New_York",
            insertEvent = { error("must not be called on edit path") },
            updateEvent = { _, draft, _, _, _, _ ->
                savedRrule = draft.rrule
                7L
            },
            setReminders = { _, _ -> true },
        )
        // 2026-12-31 23:59:59 EST = 2027-01-01 04:59:59 UTC.
        assertEquals("FREQ=DAILY;UNTIL=20270101T045959Z", savedRrule)
    }

    // F8: a timed end-before-start draft is rejected at save rather than written
    // as an inverted range (the duration floor would silently widen it to 60s).
    @Test
    fun `timed end before start returns Failure without writing`() = runBlocking {
        var inserted = false
        val inverted = form().copy(
            startDate = LocalDate.of(2026, 6, 1),
            startTime = LocalTime.of(13, 0),
            endDate = LocalDate.of(2026, 6, 1),
            endTime = LocalTime.of(12, 0),
        )
        val result =
            EventSave.attempt(
                form = inverted,
                editingEventId = null,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                insertEvent = {
                    inserted = true
                    5L
                },
                updateEvent = { _, _, _, _, _, _ -> error("must not be called") },
                setReminders = { _, _ -> true },
            )
        assertEquals(SaveResult.Failure, result)
        assertTrue("insert must not run for an inverted range", !inserted)
    }

    // All-day end date before start date is also rejected.
    @Test
    fun `all-day end date before start date returns Failure`() = runBlocking {
        var inserted = false
        val inverted = form().copy(
            allDay = true,
            startDate = LocalDate.of(2026, 6, 2),
            endDate = LocalDate.of(2026, 6, 1),
        )
        val result =
            EventSave.attempt(
                form = inverted,
                editingEventId = null,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                insertEvent = {
                    inserted = true
                    5L
                },
                updateEvent = { _, _, _, _, _, _ -> error("must not be called") },
                setReminders = { _, _ -> true },
            )
        assertEquals(SaveResult.Failure, result)
        assertTrue("insert must not run for an inverted all-day range", !inserted)
    }

    // Boundary guard: a single-day all-day event (end date == start date) is a
    // valid one-day span and must still save.
    @Test
    fun `single-day all-day event saves`() = runBlocking {
        val sameDay = form().copy(
            allDay = true,
            startDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 6, 1),
        )
        val result =
            EventSave.attempt(
                form = sameDay,
                editingEventId = null,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                insertEvent = { 5L },
                updateEvent = { _, _, _, _, _, _ -> error("must not be called") },
                setReminders = { _, _ -> true },
            )
        assertEquals(SaveResult.Success(5L), result)
    }

    // F5: editing "this and following" without touching recurrence must keep the
    // parent's rule tokens on the future split. build() can't model BYDAY, so a
    // FREQ=WEEKLY;BYDAY=MO,WE series would otherwise recur daily-by-DTSTART on the
    // split half. The split derives from the parent rule, preserving BYDAY.
    @Test
    fun `ThisAndFollowing keeps BYDAY on the split when recurrence is untouched`() = runBlocking {
        var savedRrule: String? = null
        val recurringForm = form().copy(
            recurrenceFrequency = RecurrenceFrequency.Weekly,
            recurrenceInterval = 1,
            recurrenceUntilDate = null,
            recurrenceCount = null,
        )
        EventSave.attempt(
            form = recurringForm,
            editingEventId = 7L,
            scope = RecurringEditScope.ThisAndFollowing,
            instanceMillis = 1_700_000_000_000L,
            parentRrule = "FREQ=WEEKLY;BYDAY=MO,WE",
            loadedTimezone = "UTC",
            insertEvent = { error("must not be called on edit path") },
            updateEvent = { _, draft, _, _, _, _ ->
                savedRrule = draft.rrule
                7L
            },
            setReminders = { _, _ -> true },
        )
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE", savedRrule)
    }

    // Changing only a modeled field (here the interval) rebuilds the rule but
    // must CARRY the tokens the editor cannot express: an every-2-weeks edit on
    // an imported MO,WE series must not silently collapse it to the DTSTART
    // weekday. The frequency is unchanged, so BYDAY still means the same thing.
    @Test
    fun `ThisAndFollowing carries unmodeled tokens when only the interval changes`() = runBlocking {
        var savedRrule: String? = null
        val recurringForm = form().copy(
            recurrenceFrequency = RecurrenceFrequency.Weekly,
            recurrenceInterval = 2,
            recurrenceUntilDate = null,
            recurrenceCount = null,
        )
        EventSave.attempt(
            form = recurringForm,
            editingEventId = 7L,
            scope = RecurringEditScope.ThisAndFollowing,
            instanceMillis = 1_700_000_000_000L,
            parentRrule = "FREQ=WEEKLY;BYDAY=MO,WE",
            loadedTimezone = "UTC",
            insertEvent = { error("must not be called on edit path") },
            updateEvent = { _, draft, _, _, _, _ ->
                savedRrule = draft.rrule
                7L
            },
            setReminders = { _, _ -> true },
        )
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE", savedRrule)
    }

    // Guard on the carry's boundary: changing the FREQUENCY redefines the rule,
    // and weekly BYDAY tokens mean something different under a new frequency,
    // so they are dropped rather than carried.
    @Test
    fun `ThisAndFollowing drops unmodeled tokens when the frequency changes`() = runBlocking {
        var savedRrule: String? = null
        val recurringForm = form().copy(
            recurrenceFrequency = RecurrenceFrequency.Monthly,
            recurrenceInterval = 1,
            recurrenceUntilDate = null,
            recurrenceCount = null,
        )
        EventSave.attempt(
            form = recurringForm,
            editingEventId = 7L,
            scope = RecurringEditScope.ThisAndFollowing,
            instanceMillis = 1_700_000_000_000L,
            parentRrule = "FREQ=WEEKLY;BYDAY=MO,WE",
            loadedTimezone = "UTC",
            insertEvent = { error("must not be called on edit path") },
            updateEvent = { _, draft, _, _, _, _ ->
                savedRrule = draft.rrule
                7L
            },
            setReminders = { _, _ -> true },
        )
        assertEquals("FREQ=MONTHLY", savedRrule)
    }

    // C1: editing a recurring series via "All events" from a NON-FIRST occurrence
    // must shift the parent anchor (DTSTART) by the occurrence's delta, not pin it
    // to the opened occurrence's new time. The editor seeds the form with the
    // opened occurrence's slot (extractLocalRange), so writing it straight to
    // DTSTART would jump the series forward and silently drop every earlier
    // occurrence. Here the user opened the 2026-06-15 occurrence of a weekly series
    // and moved it +1h; the parent anchor must move +1h, staying on its own date.
    @Test
    fun `AllEvents edit from a later occurrence shifts the parent anchor by the delta`() = runBlocking {
        var savedStart: Long? = null
        val parentStart = 1_000_000_000_000L
        val zone = ZoneId.systemDefault()
        val instance = LocalDate.of(2026, 6, 15).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val moved = form().copy(
            startDate = LocalDate.of(2026, 6, 15),
            startTime = LocalTime.of(13, 0),
            endDate = LocalDate.of(2026, 6, 15),
            endTime = LocalTime.of(14, 0),
            recurrenceFrequency = RecurrenceFrequency.Weekly,
            recurrenceInterval = 1,
        )
        EventSave.attempt(
            form = moved,
            editingEventId = 7L,
            scope = RecurringEditScope.AllEvents,
            instanceMillis = instance,
            parentRrule = "FREQ=WEEKLY",
            parentStartMillis = parentStart,
            loadedTimezone = zone.id,
            insertEvent = { error("must not be called on edit path") },
            updateEvent = { _, draft, _, _, _, _ ->
                savedStart = draft.startMillis
                7L
            },
            setReminders = { _, _ -> true },
        )
        // +1h occurrence move shifts the anchor +1h, NOT to 2026-06-15.
        assertEquals(parentStart + 3_600_000L, savedStart)
    }

    // C1 companion: a title-only edit (occurrence start unchanged) on "All events"
    // must leave the parent anchor exactly where it was. Pre-fix this jumped
    // DTSTART to the opened occurrence's date, dropping the earlier occurrences.
    @Test
    fun `AllEvents edit with no time change keeps the parent anchor`() = runBlocking {
        var savedStart: Long? = null
        val parentStart = 1_000_000_000_000L
        val zone = ZoneId.systemDefault()
        val instance = LocalDate.of(2026, 6, 15).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val titleOnly = form().copy(
            title = "Renamed standup",
            startDate = LocalDate.of(2026, 6, 15),
            startTime = LocalTime.of(12, 0),
            endDate = LocalDate.of(2026, 6, 15),
            endTime = LocalTime.of(13, 0),
            recurrenceFrequency = RecurrenceFrequency.Weekly,
            recurrenceInterval = 1,
        )
        EventSave.attempt(
            form = titleOnly,
            editingEventId = 7L,
            scope = RecurringEditScope.AllEvents,
            instanceMillis = instance,
            parentRrule = "FREQ=WEEKLY",
            parentStartMillis = parentStart,
            loadedTimezone = zone.id,
            insertEvent = { error("must not be called on edit path") },
            updateEvent = { _, draft, _, _, _, _ ->
                savedStart = draft.startMillis
                7L
            },
            setReminders = { _, _ -> true },
        )
        assertEquals(parentStart, savedStart)
    }

    // C1 all-day variant: all-day occurrences and the parent DTSTART are both
    // stored at UTC midnight, so an "All events" date shift must move the parent
    // anchor by whole days, not pin it to the opened occurrence's date. User
    // opened the 2026-06-15 all-day occurrence and moved it to 2026-06-16 (+1 day).
    @Test
    fun `AllEvents all-day edit shifts the parent anchor by whole days`() = runBlocking {
        var savedStart: Long? = null
        val parentStart = 1_000_000_000_000L
        val instance = LocalDate.of(2026, 6, 15).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val movedDay = form().copy(
            allDay = true,
            startDate = LocalDate.of(2026, 6, 16),
            endDate = LocalDate.of(2026, 6, 16),
            recurrenceFrequency = RecurrenceFrequency.Weekly,
            recurrenceInterval = 1,
        )
        EventSave.attempt(
            form = movedDay,
            editingEventId = 7L,
            scope = RecurringEditScope.AllEvents,
            instanceMillis = instance,
            parentRrule = "FREQ=WEEKLY",
            parentStartMillis = parentStart,
            insertEvent = { error("must not be called on edit path") },
            updateEvent = { _, draft, _, _, _, _ ->
                savedStart = draft.startMillis
                7L
            },
            setReminders = { _, _ -> true },
        )
        assertEquals(parentStart + 86_400_000L, savedStart)
    }

    // Edit path reminder rejection: same partial-failure contract as the
    // create path. Event row was updated; reminder write rejected; user
    // sees Failure. The reminder is changed (30 vs loaded 10) so the write
    // actually runs and can reject.
    @Test
    fun `edit path with reminder rejection returns Failure`() = runBlocking {
        val result =
            EventSave.attempt(
                form = form().copy(reminderMinutes = listOf(30)),
                editingEventId = 7L,
                scope = RecurringEditScope.AllEvents,
                instanceMillis = null,
                parentRrule = null,
                loadedReminderMinutes = listOf(10),
                insertEvent = { error("must not be called on edit path") },
                updateEvent = { id, _, _, _, _, _ -> id },
                setReminders = { _, _ -> false },
            )
        assertEquals(SaveResult.Failure, result)
    }
}
