/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.threeday

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// 3-day pages roll a fixed stride from an anchor (rolling, not snapped to
// a calendar boundary). page == center shows [anchor, anchor + 3).
internal const val ThreeDayPageSize = 3

// First date shown on a given page.
internal fun pageStart(anchor: LocalDate, page: Int, center: Int): LocalDate =
    anchor.plusDays((page - center).toLong() * ThreeDayPageSize)

// Page whose 3-day span contains the date. floorDiv (not integer / which
// truncates toward zero) so dates before the anchor map to the correct
// earlier page rather than rounding back toward center.
internal fun pageForDate(anchor: LocalDate, date: LocalDate, center: Int): Int {
    val days = ChronoUnit.DAYS.between(anchor, date).toInt()
    return center + Math.floorDiv(days, ThreeDayPageSize)
}
