/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import com.arishawke.asala.calendar.data.EventDraft
import com.arishawke.asala.calendar.data.RecurrenceRule
import com.arishawke.asala.calendar.data.RecurringEditScope
import java.time.ZoneId
import java.util.TimeZone

// Pure orchestration of the save flow: builds the EventDraft from the
// form, then routes through the provided suspend lambdas in the right
// order. Lives outside the ViewModel so the partial-failure contract
// (event inserted, reminder write rejected -> still SaveResult.Failure)
// can be unit-tested without spinning up a ContentResolver.
internal object EventSave {
    // Early-return chain on each save step (calendar id, insert, reminder)
    // keeps the partial-failure contract readable without nesting the
    // happy-path body. The body is intentionally one straight line through
    // the steps, so the detekt thresholds are suppressed here.
    @Suppress("LongParameterList", "LongMethod", "ReturnCount")
    suspend fun attempt(
        form: EventEditFormState,
        editingEventId: Long?,
        scope: RecurringEditScope,
        instanceMillis: Long?,
        parentRrule: String?,
        parentAllDay: Boolean = false,
        // Snapshotted from the loaded EventDetail (null on new events).
        // Threaded through so the draft preserves a Tentative / Free value
        // the user set server-side rather than clobbering it back to
        // CONFIRMED / BUSY on every local edit.
        loadedStatus: Int? = null,
        loadedAvailability: Int? = null,
        insertEvent: suspend (EventDraft) -> Long?,
        // returns the id reminders should attach to: original event id for
        // AllEvents, or the freshly-inserted exception/split id for the
        // recurring scopes. null on failure.
        updateEvent: suspend (Long, EventDraft, RecurringEditScope, Long?, String?, Boolean) -> Long?,
        setReminder: suspend (Long, Int?) -> Boolean,
    ): SaveResult {
        val calId = form.selectedCalendarId ?: return SaveResult.Failure
        val zone = ZoneId.systemDefault()
        val tz = if (form.allDay) "UTC" else TimeZone.getDefault().id

        val startMillis: Long
        val endMillis: Long
        if (form.allDay) {
            startMillis =
                form.startDate
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli()
            endMillis =
                form.endDate
                    .plusDays(1)
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli()
        } else {
            startMillis =
                form.startDate
                    .atTime(form.startTime)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            endMillis =
                form.endDate
                    .atTime(form.endTime)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
        }

        val rrule =
            form.recurrenceFrequency?.let { freq ->
                RecurrenceRule.build(
                    frequency = freq,
                    interval = form.recurrenceInterval,
                    untilUtc = form.recurrenceUntilDate,
                    count = form.recurrenceCount,
                    allDay = form.allDay,
                )
            }

        val draft =
            EventDraft(
                calendarId = calId,
                title = form.title,
                description = form.description.ifBlank { null },
                location = form.location.ifBlank { null },
                startMillis = startMillis,
                endMillis = endMillis,
                allDay = form.allDay,
                eventTimezone = tz,
                rrule = rrule,
                status = loadedStatus,
                availability = loadedAvailability,
            )

        return if (editingEventId == null) {
            val id = insertEvent(draft) ?: return SaveResult.Failure
            // event row created; if the reminder write fails the event still
            // exists on the calendar, but surface failure so the user knows
            // the reminder did not stick.
            if (!setReminder(id, form.reminderMinutesBefore)) return SaveResult.Failure
            SaveResult.Success(id)
        } else {
            // effectiveId is the row reminders should attach to: original
            // event for AllEvents; freshly-inserted exception or split row
            // for ThisInstance / ThisAndFollowing. Without this hop the
            // reminder rewrites the parent series' reminders and the new
            // exception ships without one.
            val effectiveId = updateEvent(editingEventId, draft, scope, instanceMillis, parentRrule, parentAllDay)
                ?: return SaveResult.Failure
            if (!setReminder(effectiveId, form.reminderMinutesBefore)) return SaveResult.Failure
            SaveResult.Success(effectiveId)
        }
    }
}
