/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.TimeZone

// Field assembly for a "this occurrence only" edit: start from the draft, bind
// it to the parent series' slot, and strip recurrence so the exception is a
// one-off. Pure + map-shaped so it unit-tests without a ContentResolver, like
// EventCancellation.buildMap.
internal fun thisInstanceExceptionMap(
    draft: EventDraft,
    parentEventId: Long,
    instanceMillis: Long,
    parentAllDay: Boolean,
): Map<String, Any?> = buildMap {
    putAll(draft.toMap())
    put(CalendarContract.Events.ORIGINAL_ID, parentEventId)
    put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, instanceMillis)
    put(CalendarContract.Events.ORIGINAL_ALL_DAY, if (parentAllDay) 1 else 0)
    remove(CalendarContract.Events.RRULE)
    remove(CalendarContract.Events.DURATION)
    put(CalendarContract.Events.DTEND, draft.endMillis)
}

// A recurring parent's truncation must re-send DTSTART, not just the new RRULE:
// CalendarProvider only rebuilds the Instances table when the update delta
// carries DTSTART (it early-returns "Missing DTSTART. No need to update
// instance." otherwise), so an rrule-only update leaves stale occurrences past
// the new UNTIL. Mirrors Etar's updatePastEvents. Pure + map-shaped to unit-test.
internal fun parentTruncationMap(parentDtStart: Long?, newRrule: String): Map<String, Any?> = buildMap {
    if (parentDtStart != null) put(CalendarContract.Events.DTSTART, parentDtStart)
    put(CalendarContract.Events.RRULE, newRrule)
}

// Reads the parent's own (unchanged) DTSTART and re-sends it with the truncated
// RRULE so the provider regenerates instances. Returns the update row count.
// callers invoke it from a Dispatchers.IO context.
private fun ContentResolver.truncateParentRecurrence(eventId: Long, newRrule: String): Int {
    val parentUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
    val dtStart =
        query(parentUri, arrayOf(CalendarContract.Events.DTSTART), null, null, null)
            ?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                    if (it.isNull(idx)) null else it.getLong(idx)
                } else {
                    null
                }
            }
    if (dtStart == null) {
        Timber.w(
            "truncateParentRecurrence: parent %d DTSTART unavailable; rrule-only update may leave stale instances",
            eventId,
        )
    }
    val cv = parentTruncationMap(dtStart, newRrule).toCalendarEventContentValues()
    return update(parentUri, cv, null, null)
}

// Recurring-scope-aware update. Returns the row id that follow-up writes
// (reminders, attachments) should target: the original event for AllEvents,
// or the freshly-inserted exception/split row for the recurring scopes.
// null on failure. Without that distinction, reminders for a per-instance
// edit silently overwrite the parent series' reminders.
internal suspend fun ContentResolver.updateEventScoped(
    eventId: Long,
    draft: EventDraft,
    scope: RecurringEditScope = RecurringEditScope.AllEvents,
    instanceMillis: Long? = null,
    parentRrule: String? = null,
    parentAllDay: Boolean = false,
): Long? = withContext(Dispatchers.IO) {
    when (scope) {
        RecurringEditScope.AllEvents -> {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            if (update(uri, draft.toContentValues(), null, null) > 0) eventId else null
        }
        RecurringEditScope.ThisInstance -> {
            require(instanceMillis != null)
            val cv = thisInstanceExceptionMap(draft, eventId, instanceMillis, parentAllDay)
                .toCalendarEventContentValues()
            val uri = insert(CalendarContract.Events.CONTENT_URI, cv) ?: return@withContext null
            ContentUris.parseId(uri).takeIf { it > 0L }
        }
        RecurringEditScope.ThisAndFollowing -> {
            require(instanceMillis != null && parentRrule != null)
            val newRrule =
                RecurrenceExceptionMath.appendUntil(
                    parentRrule,
                    RecurrenceExceptionMath.untilUtcForTruncation(instanceMillis, parentAllDay),
                )
            // Insert the split series first. If the parent truncation then
            // fails the user sees a recoverable duplicate rather than silently
            // losing the following occurrences (survive-over-rollback, as in
            // the save layer).
            val uri = insert(CalendarContract.Events.CONTENT_URI, draft.toContentValues())
                ?: return@withContext null
            val newId = ContentUris.parseId(uri).takeIf { it > 0L } ?: return@withContext null
            if (truncateParentRecurrence(eventId, newRrule) <= 0) {
                Timber.e(
                    "ThisAndFollowing: split %d inserted but parent %d truncation failed",
                    newId,
                    eventId,
                )
            }
            newId
        }
    }
}

internal suspend fun ContentResolver.deleteEventScoped(
    eventId: Long,
    scope: RecurringEditScope = RecurringEditScope.AllEvents,
    instanceMillis: Long? = null,
    parentRrule: String? = null,
    parentCalendarId: Long? = null,
    parentAllDay: Boolean = false,
): Boolean = withContext(Dispatchers.IO) {
    when (scope) {
        RecurringEditScope.AllEvents -> {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            delete(uri, null, null) > 0
        }
        RecurringEditScope.ThisInstance -> {
            require(instanceMillis != null && parentCalendarId != null)
            val cv =
                EventCancellation
                    .buildMap(
                        parentEventId = eventId,
                        parentCalendarId = parentCalendarId,
                        instanceMillis = instanceMillis,
                        timezoneId = TimeZone.getDefault().id,
                        parentAllDay = parentAllDay,
                    ).toCalendarEventContentValues()
            insert(CalendarContract.Events.CONTENT_URI, cv) != null
        }
        RecurringEditScope.ThisAndFollowing -> {
            require(instanceMillis != null && parentRrule != null)
            val newRrule =
                RecurrenceExceptionMath.appendUntil(
                    parentRrule,
                    RecurrenceExceptionMath.untilUtcForTruncation(instanceMillis, parentAllDay),
                )
            truncateParentRecurrence(eventId, newRrule) > 0
        }
    }
}
