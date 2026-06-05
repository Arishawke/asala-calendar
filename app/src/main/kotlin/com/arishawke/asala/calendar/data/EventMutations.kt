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
import android.content.ContentValues
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

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

private data class ParentRecurrence(val dtStart: Long?, val rrule: String?, val exdate: String?)

// "delete this occurrence only" without an exception row: append the occurrence
// to the parent's EXDATE. an exception row (STATUS_CANCELED or otherwise) makes
// the provider drop the whole series from the Instances expansion on-device, so
// excluding the date on the parent is the reliable path. DTSTART is re-sent in
// the same delta because the provider only rebuilds the Instances table when the
// update carries DTSTART. caller is on Dispatchers.IO.
@Suppress("ReturnCount") // linear early-return chain on each provider step
private fun ContentResolver.excludeInstanceFromParent(
    eventId: Long,
    instanceMillis: Long,
    parentAllDay: Boolean,
): Boolean {
    val parentUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
    val projection =
        arrayOf(CalendarContract.Events.DTSTART, CalendarContract.Events.RRULE, CalendarContract.Events.EXDATE)
    val parent =
        query(parentUri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return false
            val dtStartIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val rruleIdx = c.getColumnIndexOrThrow(CalendarContract.Events.RRULE)
            val exdateIdx = c.getColumnIndexOrThrow(CalendarContract.Events.EXDATE)
            ParentRecurrence(
                dtStart = if (c.isNull(dtStartIdx)) null else c.getLong(dtStartIdx),
                rrule = c.getString(rruleIdx),
                exdate = c.getString(exdateIdx),
            )
        } ?: return false
    // re-send DTSTART + RRULE in the same delta: the provider only rebuilds the
    // Instances table when DTSTART is present, and recomputes the series end
    // (lastDate) correctly only when RRULE is too. Without RRULE, an EXDATE-only
    // update truncates the series at the excluded date.
    val cv =
        ContentValues().apply {
            if (parent.dtStart != null) put(CalendarContract.Events.DTSTART, parent.dtStart)
            if (parent.rrule != null) put(CalendarContract.Events.RRULE, parent.rrule)
            put(
                CalendarContract.Events.EXDATE,
                RecurrenceExceptionMath.mergeExdate(
                    parent.exdate,
                    RecurrenceExceptionMath.exdateValue(instanceMillis, parentAllDay),
                ),
            )
        }
    return update(parentUri, cv, null, null) > 0
}

// pure core of countParentInstancesBefore: counts provider-expanded instance
// starts strictly before the split point. range queries are overlap-inclusive,
// so the split instance itself (begin == beforeMillis) is excluded. extracted
// so this rule is unit-testable without a ContentResolver.
internal fun countInstancesBefore(begins: List<Long>, beforeMillis: Long): Int = begins.count { it < beforeMillis }

// the COUNT budget is divided across parent + future only when the user left the
// inherited count untouched. if they retyped it (or switched the split to UNTIL /
// open-ended), honor the split rule as-is rather than subtracting the parent's
// kept occurrences from a value the user chose.
internal fun shouldReduceSplitCount(parentRrule: String?, splitRrule: String): Boolean {
    val parentCount = RecurrenceRule.countOf(parentRrule) ?: return false
    return RecurrenceRule.countOf(splitRrule) == parentCount
}

// counts the parent's occurrences in [fromMillis, beforeMillis) via the
// provider's own expansion (BYDAY/INTERVAL-proof) so a split can carry the
// remaining COUNT. returns 0 if instances can't be read, which degrades to the
// prior full-count behaviour rather than dropping occurrences.
private fun ContentResolver.countParentInstancesBefore(eventId: Long, fromMillis: Long, beforeMillis: Long): Int {
    if (beforeMillis <= fromMillis) return 0
    val uri = instancesUriFor(fromMillis, beforeMillis)
    val begins =
        query(
            uri,
            arrayOf(CalendarContract.Instances.BEGIN),
            "${CalendarContract.Instances.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { c ->
            val beginIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            buildList<Long> { while (c.moveToNext()) add(c.getLong(beginIdx)) }
        }.orEmpty()
    return countInstancesBefore(begins, beforeMillis)
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
            // a provider exception row (the documented CONTENT_EXCEPTION_URI path
            // included) drops the whole series from the Instances expansion on
            // device. So model a single-occurrence edit as: insert the edited
            // occurrence as a standalone one-off, then exclude the original date
            // on the parent. insert first so a failed EXDATE write can roll the
            // one-off back to a clean failure instead of silently losing the
            // occurrence. the new row is returned so reminders target it.
            val oneOff = draft.copy(rrule = null)
            val uri = insert(CalendarContract.Events.CONTENT_URI, oneOff.toContentValues()) ?: return@withContext null
            val newId = ContentUris.parseId(uri).takeIf { it > 0L } ?: return@withContext null
            if (!excludeInstanceFromParent(eventId, instanceMillis, parentAllDay)) {
                delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, newId), null, null)
                return@withContext null
            }
            newId
        }
        RecurringEditScope.ThisAndFollowing -> {
            require(instanceMillis != null && parentRrule != null)
            updateThisAndFollowing(eventId, draft, instanceMillis, parentRrule, parentAllDay)
        }
    }
}

// the "this and following" split, extracted to keep updateEventScoped's branch
// count under the complexity gate. caller is on Dispatchers.IO.
@Suppress("ReturnCount") // linear early-return chain on each provider step
private fun ContentResolver.updateThisAndFollowing(
    eventId: Long,
    draft: EventDraft,
    instanceMillis: Long,
    parentRrule: String,
    parentAllDay: Boolean,
): Long? {
    val parentDtStart = queryEventDtStart(eventId)
    // "this and following" from the first occurrence covers the whole series:
    // update the parent in place rather than truncating it to an occurrence-less
    // row and inserting a duplicate split. both values are direct reads, so this
    // never mis-fires the way an Instances count can.
    if (parentDtStart != null && instanceMillis <= parentDtStart) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return if (update(uri, draft.toContentValues(), null, null) > 0) eventId else null
    }
    val splitRrule = draft.rrule
    // preserve the total occurrence count: a COUNT-bounded series must split its
    // COUNT across parent + future, else the future series regenerates the full
    // count from its new anchor and over-generates. only when the split still
    // carries the inherited count, so a count the user retyped is honored as-is.
    val splitDraft =
        if (splitRrule != null && parentDtStart != null && shouldReduceSplitCount(parentRrule, splitRrule)) {
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
    // insert the split first: if truncation fails the user sees a recoverable
    // duplicate rather than losing following occurrences.
    val uri = insert(CalendarContract.Events.CONTENT_URI, splitDraft.toContentValues()) ?: return null
    val newId = ContentUris.parseId(uri).takeIf { it > 0L } ?: return null
    if (truncateParentRecurrence(eventId, parentDtStart, newRrule) <= 0) {
        Timber.e("ThisAndFollowing: split %d inserted but parent %d truncation failed", newId, eventId)
    }
    return newId
}

internal suspend fun ContentResolver.deleteEventScoped(
    eventId: Long,
    scope: RecurringEditScope = RecurringEditScope.AllEvents,
    instanceMillis: Long? = null,
    parentRrule: String? = null,
    parentAllDay: Boolean = false,
): Boolean = withContext(Dispatchers.IO) {
    when (scope) {
        RecurringEditScope.AllEvents -> {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            delete(uri, null, null) > 0
        }
        RecurringEditScope.ThisInstance -> {
            require(instanceMillis != null)
            excludeInstanceFromParent(eventId, instanceMillis, parentAllDay)
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
