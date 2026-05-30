/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

import com.arishawke.asala.calendar.data.TimeUnits
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

// Drag-to-reschedule math, kept pure so it can be unit-tested without
// Compose. Vertical drag delta in pixels -> minute delta relative to the
// event's original start, snapped to a 15-minute grid.
internal const val ScheduleSnapMinutes = 15

internal fun pxToMinutes(deltaPx: Float, hourHeightPx: Float): Int {
    if (hourHeightPx <= 0f) return 0
    return (deltaPx / hourHeightPx * TimeUnits.MinutesPerHour).roundToInt()
}

internal fun snapToGrid(minutes: Int, snapMinutes: Int = ScheduleSnapMinutes): Int {
    if (snapMinutes <= 0) return minutes
    val rounded = (minutes.toDouble() / snapMinutes).roundToInt()
    return rounded * snapMinutes
}

internal fun applyMinuteDelta(originalMillis: Long, deltaMinutes: Int): Long =
    originalMillis + deltaMinutes * TimeUnits.MillisPerMinute

// Shifts an event by N days plus M minutes, walking through the zone's
// DST transitions. A flat (24h * dayDelta) add would silently absorb
// or duplicate the DST hour around the spring-forward and fall-back
// boundaries; ZonedDateTime.plusDays preserves local wall-clock time
// across them, which is the behavior users expect for drag-reschedule.
internal fun applyDayAndMinuteDelta(originalMillis: Long, zone: ZoneId, dayDelta: Int, minuteDelta: Int): Long {
    val shifted = Instant.ofEpochMilli(originalMillis)
        .atZone(zone)
        .plusDays(dayDelta.toLong())
        .plusMinutes(minuteDelta.toLong())
    return shifted.toInstant().toEpochMilli()
}

// Pixel-to-column delta for cross-day drag, snapped on release.
// Negative result = drag toward earlier days, positive = later days.
internal fun pxToDayDelta(deltaPx: Float, columnWidthPx: Float): Int {
    if (columnWidthPx <= 0f) return 0
    return (deltaPx / columnWidthPx).roundToInt()
}

// Clamps a day delta so the resulting day stays within the visible
// window. Drag does not auto-scroll into the adjacent week when the
// gesture overshoots the edge.
internal fun clampDayDelta(dayDelta: Int, currentColumn: Int, totalColumns: Int): Int {
    if (totalColumns <= 1) return 0
    val minDelta = -currentColumn
    val maxDelta = (totalColumns - 1) - currentColumn
    return dayDelta.coerceIn(minDelta, maxDelta)
}
