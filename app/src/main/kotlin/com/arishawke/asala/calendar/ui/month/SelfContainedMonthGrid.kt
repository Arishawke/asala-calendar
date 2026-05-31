/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import com.arishawke.asala.calendar.ui.multidaybars.WeekSegment
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition

// Pure helpers for the self-contained (continuous) Month surface, where
// each month renders only its own days. buildMonthGrid still seeds 42
// days; these trim the empty trailing week and clip multi-day bars to the
// month edge so the blanked adjacent-month cells stay empty.

private const val DaysPerWeek = 7

// Weeks holding at least one in-month day. Day 1 always lands in week 0,
// so only trailing weeks can be all-filler; rendering through the last
// in-month week drops the empty 6th row a 5-week month would show.
internal fun weeksWithMonthDays(days: List<CalendarDay>): Int {
    val lastIn = days.indexOfLast { it.position == DayPosition.MonthDate }
    return if (lastIn < 0) 0 else lastIn / DaysPerWeek + 1
}

// Clip bar segments to a week's in-month columns so bars stop at the
// month edge. Squares the cut side via the continuation flags.
internal fun clipSegmentsToColumns(
    segments: List<WeekSegment>,
    firstInMonthCol: Int,
    lastInMonthCol: Int,
): List<WeekSegment> {
    if (firstInMonthCol < 0) return emptyList()
    return segments.mapNotNull { seg ->
        if (seg.endCol < firstInMonthCol || seg.startCol > lastInMonthCol) {
            null
        } else {
            seg.copy(
                startCol = seg.startCol.coerceAtLeast(firstInMonthCol),
                endCol = seg.endCol.coerceAtMost(lastInMonthCol),
                isContinuedLeft = seg.isContinuedLeft || seg.startCol < firstInMonthCol,
                isContinuedRight = seg.isContinuedRight || seg.endCol > lastInMonthCol,
            )
        }
    }
}
