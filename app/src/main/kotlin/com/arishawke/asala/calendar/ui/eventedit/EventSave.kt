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

// builds the EventDraft and routes through the suspend lambdas. lives outside
// the ViewModel so the partial-failure contract (event inserted, reminder
// rejected -> still Failure) is unit-testable without a ContentResolver.
internal object EventSave {
    // early-return chain per save step keeps the partial-failure contract
    // flat; detekt thresholds suppressed for the intentionally linear body.
    @Suppress("LongParameterList", "LongMethod", "ReturnCount", "CyclomaticComplexMethod")
    suspend fun attempt(
        form: EventEditFormState,
        editingEventId: Long?,
        scope: RecurringEditScope,
        instanceMillis: Long?,
        parentRrule: String?,
        parentAllDay: Boolean = false,
        // null on new events. preserves a server-set Tentative / Free value
        // rather than clobbering it to CONFIRMED / BUSY on every local edit.
        loadedStatus: Int? = null,
        loadedAvailability: Int? = null,
        // preserve the authored EVENT_TIMEZONE on edit; only new events fall back
        // to the device zone. clobbering it shifts the intended-zone occurrences.
        loadedTimezone: String? = null,
        insertEvent: suspend (EventDraft) -> Long?,
        // returns the id reminders attach to: original for AllEvents, or the
        // new exception/split id for the recurring scopes. null on failure.
        updateEvent: suspend (Long, EventDraft, RecurringEditScope, Long?, String?, Boolean) -> Long?,
        setReminder: suspend (Long, Int?) -> Boolean,
    ): SaveResult {
        val calId = form.selectedCalendarId ?: return SaveResult.Failure
        val zone = ZoneId.systemDefault()
        val tz = if (form.allDay) "UTC" else (loadedTimezone ?: TimeZone.getDefault().id)
        // the event zone drives the recurrence UNTIL cutoff; fall back to the
        // device zone if a stored EVENT_TIMEZONE can't be parsed.
        val eventZone = runCatching { ZoneId.of(tz) }.getOrDefault(zone)

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
        // reject an inverted or zero-length range rather than writing it: the
        // EventDraft duration floor would otherwise silently widen it to 60s
        // (or one day for all-day). all-day end is exclusive, so a single-day
        // event has endMillis a day past startMillis and still passes.
        if (endMillis <= startMillis) return SaveResult.Failure

        val rrule =
            form.recurrenceFrequency?.let { freq ->
                // if the user left recurrence untouched, keep the loaded rule
                // verbatim rather than rebuilding. build() only models
                // FREQ/INTERVAL/UNTIL/COUNT, so a rebuild would drop a sub-day
                // UNTIL time (widening it to ...235959Z) or BYDAY/WKST tokens.
                // Applies to the whole-series edit and to the this-and-following
                // split's future series (whose COUNT is still reduced downstream),
                // so an imported BYDAY series keeps its days on the split half.
                val keepOriginal =
                    scope != RecurringEditScope.ThisInstance &&
                        parentRrule != null &&
                        RecurrenceRule.matchesEditorFields(
                            parentRrule,
                            freq,
                            form.recurrenceInterval,
                            form.recurrenceUntilDate,
                            form.recurrenceCount,
                            eventZone,
                        )
                if (keepOriginal) {
                    parentRrule
                } else {
                    RecurrenceRule.build(
                        frequency = freq,
                        interval = form.recurrenceInterval,
                        untilDate = form.recurrenceUntilDate,
                        count = form.recurrenceCount,
                        allDay = form.allDay,
                        zoneId = eventZone,
                    )
                }
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
            // event exists even if the reminder write fails; surface failure
            // so the user knows the reminder did not stick.
            if (!setReminder(id, form.reminderMinutesBefore)) return SaveResult.Failure
            SaveResult.Success(id)
        } else {
            // effectiveId is the row reminders attach to: original for
            // AllEvents, else the new exception/split row. without this hop the
            // reminder rewrites the parent series and the exception ships none.
            val effectiveId = updateEvent(editingEventId, draft, scope, instanceMillis, parentRrule, parentAllDay)
                ?: return SaveResult.Failure
            if (!setReminder(effectiveId, form.reminderMinutesBefore)) return SaveResult.Failure
            SaveResult.Success(effectiveId)
        }
    }
}
