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
