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

// pages roll a fixed stride from the anchor, not snapped to a calendar
// boundary. page == center shows [anchor, anchor + 3).
internal const val ThreeDayPageSize = 3

internal fun pageStart(anchor: LocalDate, page: Int, center: Int): LocalDate =
    anchor.plusDays((page - center).toLong() * ThreeDayPageSize)

// floorDiv (not truncating /) so dates before the anchor map to the
// earlier page rather than rounding toward center.
internal fun pageForDate(anchor: LocalDate, date: LocalDate, center: Int): Int {
    val days = ChronoUnit.DAYS.between(anchor, date).toInt()
    return center + Math.floorDiv(days, ThreeDayPageSize)
}
