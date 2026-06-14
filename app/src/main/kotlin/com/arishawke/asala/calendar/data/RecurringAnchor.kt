/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

// applying a reschedule or edit to "all events" of a recurring series must move
// the parent anchor (DTSTART) by how far the edited occurrence moved, not pin the
// series to that occurrence's new absolute time. pinning jumps the series start
// forward to the dragged/opened occurrence and silently drops every earlier
// occurrence. returns the parent's new (start, end): start shifted by the
// occurrence delta, carrying the occurrence's (possibly edited) duration. when the
// occurrence IS the parent anchor (first occurrence, or a non-recurring event)
// the delta is zero-based and this is the identity, so callers can apply it
// unconditionally on the AllEvents path.
internal fun allEventsAnchorRange(
    parentStartMillis: Long,
    instanceStartMillis: Long,
    newStartMillis: Long,
    newEndMillis: Long,
): Pair<Long, Long> {
    val shiftedStart = parentStartMillis + (newStartMillis - instanceStartMillis)
    return shiftedStart to shiftedStart + (newEndMillis - newStartMillis)
}

internal data class RescheduleDraftShape(val rrule: String?, val startMillis: Long, val endMillis: Long)

// the draft shape a drag-reschedule writes for a chosen scope, split out of
// AppViewModel.saveRescheduleNow so the (historically data-lossy) AllEvents path is
// unit-testable. ThisInstance drops the rrule (a one-off exception) and writes the
// dragged time; AllEvents keeps the rrule and shifts the PARENT anchor by the
// occurrence delta so earlier occurrences survive (identity for a non-recurring
// event, where instance == parent); the per-occurrence scopes keep the rrule and
// write the new time as-is.
@Suppress("LongParameterList") // scope + parent rule/anchor + dragged instance + new range
internal fun rescheduleDraftShape(
    scope: RecurringEditScope,
    parentRrule: String?,
    parentStartMillis: Long,
    instanceStartMillis: Long,
    newStartMillis: Long,
    newEndMillis: Long,
): RescheduleDraftShape {
    val rrule = if (scope == RecurringEditScope.ThisInstance) null else parentRrule
    val (start, end) =
        if (scope == RecurringEditScope.AllEvents) {
            allEventsAnchorRange(parentStartMillis, instanceStartMillis, newStartMillis, newEndMillis)
        } else {
            newStartMillis to newEndMillis
        }
    return RescheduleDraftShape(rrule = rrule, startMillis = start, endMillis = end)
}
