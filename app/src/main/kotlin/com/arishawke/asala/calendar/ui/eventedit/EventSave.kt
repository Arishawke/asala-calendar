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
import com.arishawke.asala.calendar.data.allEventsAnchorRange
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
        // the parent series' DTSTART, used to rebase an AllEvents edit that was
        // opened from a later occurrence (the form is seeded with that
        // occurrence's slot). null for new/non-recurring events.
        parentStartMillis: Long? = null,
        // null on new events. preserves a server-set Tentative / Free value
        // rather than clobbering it to CONFIRMED / BUSY on every local edit.
        loadedStatus: Int? = null,
        loadedAvailability: Int? = null,
        // preserve the authored EVENT_TIMEZONE on edit; only new events fall back
        // to the device zone. clobbering it shifts the intended-zone occurrences.
        loadedTimezone: String? = null,
        // the event's loaded editable reminder set (non-negative offsets). skipped
        // on an in-place edit whose set is unchanged, so a multi-reminder event
        // does not lose rows to the delete-then-insert. empty for new events.
        loadedReminderMinutes: List<Int> = emptyList(),
        // negative-offset rows carried on the loaded event: not authorable, written
        // back verbatim with the visible set so an edit does not drop them.
        preservedReminderMinutes: List<Int> = emptyList(),
        insertEvent: suspend (EventDraft) -> Long?,
        // returns the id reminders attach to: original for AllEvents, or the
        // new exception/split id for the recurring scopes. null on failure.
        updateEvent: suspend (Long, EventDraft, RecurringEditScope, Long?, String?, Boolean) -> Long?,
        setReminders: suspend (Long, List<Int>) -> Boolean,
    ): SaveResult {
        val calId = form.selectedCalendarId ?: return SaveResult.Failure
        val zone = ZoneId.systemDefault()
        val tz = if (form.allDay) "UTC" else (loadedTimezone ?: TimeZone.getDefault().id)
        // the event zone drives the recurrence UNTIL cutoff; fall back to the
        // device zone if a stored EVENT_TIMEZONE can't be parsed. note the
        // start/end instants below are interpreted in the device `zone` (the same
        // zone the editor displays the clock in), while EVENT_TIMEZONE keeps the
        // authored value: a clock edited on a device in a different zone than the
        // event's is interpreted as device-local. preserving the authored zone is
        // still correct for the common edit (clock untouched) and avoids the prior
        // clobber-to-device-zone bug.
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

        // a recurrence whose UNTIL date precedes the start expands to zero
        // occurrences; the provider accepts it and the event silently vanishes.
        // reject it here (the editor's date picker also blocks the bad pick).
        if (form.recurrenceFrequency != null &&
            form.recurrenceUntilDate?.isBefore(form.startDate) == true
        ) {
            return SaveResult.Failure
        }

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
                    val rebuilt = RecurrenceRule.build(
                        frequency = freq,
                        interval = form.recurrenceInterval,
                        untilDate = form.recurrenceUntilDate,
                        count = form.recurrenceCount,
                        allDay = form.allDay,
                        zoneId = eventZone,
                    )
                    // an interval/end edit on a loaded rule must not strip the
                    // tokens build() cannot express (BYDAY, WKST, ...). carried
                    // only while the frequency is unchanged: weekly BYDAY means
                    // something else under a new frequency.
                    val carried =
                        if (scope != RecurringEditScope.ThisInstance &&
                            RecurrenceRule.frequencyOf(parentRrule) == freq
                        ) {
                            RecurrenceRule.unmodeledParts(parentRrule)
                        } else {
                            emptyList()
                        }
                    (listOf(rebuilt) + carried).joinToString(";")
                }
            }

        // AllEvents on a recurring series opened from a specific occurrence: the
        // form holds that occurrence's slot, so shift the parent anchor by the
        // occurrence's delta instead of writing the slot straight to DTSTART
        // (which would jump the series forward and drop every earlier occurrence).
        // Other scopes (and non-recurring / first-occurrence edits) write as-is.
        val (effectiveStart, effectiveEnd) =
            if (scope == RecurringEditScope.AllEvents && parentRrule != null) {
                // opened from an occurrence: rebase onto the parent anchor so
                // earlier occurrences survive. first-occurrence/non-instance edits
                // fall through to the form values.
                if (instanceMillis != null && parentStartMillis != null) {
                    allEventsAnchorRange(parentStartMillis, instanceMillis, startMillis, endMillis)
                } else {
                    startMillis to endMillis
                }
            } else {
                startMillis to endMillis
            }

        val draft =
            EventDraft(
                calendarId = calId,
                title = form.title,
                description = form.description.ifBlank { null },
                location = form.location.ifBlank { null },
                startMillis = effectiveStart,
                endMillis = effectiveEnd,
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
            if (!setReminders(id, form.reminderMinutes)) return SaveResult.Failure
            SaveResult.Success(id)
        } else {
            // effectiveId is the row reminders attach to: original for
            // AllEvents, else the new exception/split row. without this hop the
            // reminder rewrites the parent series and the exception ships none.
            val effectiveId = updateEvent(editingEventId, draft, scope, instanceMillis, parentRrule, parentAllDay)
                ?: return SaveResult.Failure
            // setReminders deletes every reminder row then inserts one per distinct
            // value, so an in-place edit with an unchanged set would needlessly
            // rewrite (and a stale single-value path would drop) a multi-reminder
            // event's extras. skip only when the target is the original row AND the
            // non-negative set is unchanged; a new exception/split row has no
            // reminders yet and must always be written.
            val reminderUnchanged = effectiveId == editingEventId &&
                form.reminderMinutes.normalizedReminders() == loadedReminderMinutes.normalizedReminders()
            if (!reminderUnchanged &&
                !setReminders(effectiveId, form.reminderMinutes + preservedReminderMinutes)
            ) {
                return SaveResult.Failure
            }
            SaveResult.Success(effectiveId)
        }
    }
}

// compare reminder sets: order and duplicates are not meaningful, and the editor
// only authors non-negative offsets, so distinct + sorted is the canonical form.
private fun List<Int>.normalizedReminders(): List<Int> = distinct().sorted()
