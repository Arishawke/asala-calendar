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

// drag-reschedule math, pure so it unit-tests without Compose.
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

// tap-create: a y offset inside a day column resolves to the snapped minutes
// of day, clamped so a bottom-edge tap still yields a start inside the day.
internal fun minutesAtY(yPx: Float, hourHeightPx: Float): Int {
    val minutesPerDay = TimeUnits.HoursPerDay * TimeUnits.MinutesPerHour
    if (hourHeightPx <= 0f) return 0
    val raw = (yPx / hourHeightPx * TimeUnits.MinutesPerHour).toInt()
    return snapToGrid(raw).coerceIn(0, minutesPerDay - ScheduleSnapMinutes)
}

// shifts by N days + M minutes via ZonedDateTime so wall-clock time is
// preserved across DST; a flat 24h*dayDelta add would absorb/duplicate the
// DST hour at the spring-forward/fall-back boundaries.
internal fun applyDayAndMinuteDelta(originalMillis: Long, zone: ZoneId, dayDelta: Int, minuteDelta: Int): Long {
    val shifted = Instant.ofEpochMilli(originalMillis)
        .atZone(zone)
        .plusDays(dayDelta.toLong())
        .plusMinutes(minuteDelta.toLong())
    return shifted.toInstant().toEpochMilli()
}

// pixel-to-column delta for cross-day drag. negative = earlier days, positive = later.
internal fun pxToDayDelta(deltaPx: Float, columnWidthPx: Float): Int {
    if (columnWidthPx <= 0f) return 0
    return (deltaPx / columnWidthPx).roundToInt()
}

// clamps the day delta to the visible window; no auto-scroll into the next week on overshoot.
internal fun clampDayDelta(dayDelta: Int, currentColumn: Int, totalColumns: Int): Int {
    if (totalColumns <= 1) return 0
    val minDelta = -currentColumn
    val maxDelta = (totalColumns - 1) - currentColumn
    return dayDelta.coerceIn(minDelta, maxDelta)
}
