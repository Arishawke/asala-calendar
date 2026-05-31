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

// "this occurrence only" edit: bind the draft to the parent's slot and strip
// recurrence so the exception is a one-off.
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

// truncation must re-send DTSTART, not just RRULE: the provider only rebuilds
// the Instances table when the delta carries DTSTART (else "Missing DTSTART.
// No need to update instance."), so rrule-only leaves stale occurrences past UNTIL.
internal fun parentTruncationMap(parentDtStart: Long?, newRrule: String): Map<String, Any?> = buildMap {
    if (parentDtStart != null) put(CalendarContract.Events.DTSTART, parentDtStart)
    put(CalendarContract.Events.RRULE, newRrule)
}

private fun ContentResolver.queryEventDtStart(eventId: Long): Long? {
    val parentUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
    return query(parentUri, arrayOf(CalendarContract.Events.DTSTART), null, null, null)
        ?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                if (it.isNull(idx)) null else it.getLong(idx)
            } else {
                null
            }
        }
}

// counts the parent's occurrences in [fromMillis, beforeMillis) via the
// provider's own expansion (BYDAY/INTERVAL-proof) so a split can carry the
// remaining COUNT. returns 0 if instances can't be read, which degrades to the
// prior full-count behaviour rather than dropping occurrences.
private fun ContentResolver.countParentInstancesBefore(eventId: Long, fromMillis: Long, beforeMillis: Long): Int {
    if (beforeMillis <= fromMillis) return 0
    val uri =
        CalendarContract.Instances.CONTENT_URI.buildUpon().run {
            ContentUris.appendId(this, fromMillis)
            ContentUris.appendId(this, beforeMillis)
            build()
        }
    return query(
        uri,
        arrayOf(CalendarContract.Instances.BEGIN),
        "${CalendarContract.Instances.EVENT_ID} = ?",
        arrayOf(eventId.toString()),
        null,
    )?.use { c ->
        val beginIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
        var kept = 0
        while (c.moveToNext()) {
            // range queries are overlap-inclusive; exclude the split instance itself.
            if (c.getLong(beginIdx) < beforeMillis) kept++
        }
        kept
    } ?: 0
}

// re-sends the parent's own DTSTART with the truncated RRULE so the provider
// regenerates instances. caller is on Dispatchers.IO.
private fun ContentResolver.truncateParentRecurrence(eventId: Long, parentDtStart: Long?, newRrule: String): Int {
    if (parentDtStart == null) {
        Timber.w(
            "truncateParentRecurrence: parent %d DTSTART unavailable; rrule-only update may leave stale instances",
            eventId,
        )
    }
    val parentUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
    val cv = parentTruncationMap(parentDtStart, newRrule).toCalendarEventContentValues()
    return update(parentUri, cv, null, null)
}

// scope-aware update. returns the row id follow-up writes (reminders) target:
// original event for AllEvents, the new exception/split row otherwise; null on
// failure. without it, per-instance reminders overwrite the parent's.
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
            val parentDtStart = queryEventDtStart(eventId)
            val splitRrule = draft.rrule
            // preserve the total occurrence count: a COUNT-bounded series must
            // split its COUNT across parent + future, else the future series
            // regenerates the full count from its new anchor and over-generates.
            // gate on the PARENT being COUNT-bounded (the budget to divide);
            // reduceSplitCount no-ops on UNTIL / open-ended split rules.
            val splitDraft =
                if (splitRrule != null && parentDtStart != null && RecurrenceRule.countOf(parentRrule) != null) {
                    val kept = countParentInstancesBefore(eventId, parentDtStart, instanceMillis)
                    draft.copy(rrule = RecurrenceExceptionMath.reduceSplitCount(splitRrule, kept))
                } else {
                    draft
                }
            val newRrule =
                RecurrenceExceptionMath.appendUntil(
                    parentRrule,
                    RecurrenceExceptionMath.untilUtcForTruncation(instanceMillis, parentAllDay),
                )
            // insert the split first: if truncation fails the user sees a
            // recoverable duplicate rather than losing following occurrences.
            val uri = insert(CalendarContract.Events.CONTENT_URI, splitDraft.toContentValues())
                ?: return@withContext null
            val newId = ContentUris.parseId(uri).takeIf { it > 0L } ?: return@withContext null
            if (truncateParentRecurrence(eventId, parentDtStart, newRrule) <= 0) {
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
            truncateParentRecurrence(eventId, queryEventDtStart(eventId), newRrule) > 0
        }
    }
}
