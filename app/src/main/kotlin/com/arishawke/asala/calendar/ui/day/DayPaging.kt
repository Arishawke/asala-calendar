/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.day

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// pure page<->date mapping for the day pager. the window is wide (about five
// years each side, matching the week pager) so a jump from the month view lands
// on the exact day instead of clamping to a near edge.
object DayPaging {
    const val WindowDaysEachSide: Long = 1825L

    val pageCount: Int = (WindowDaysEachSide * 2 + 1).toInt()
    val todayPageIndex: Int = WindowDaysEachSide.toInt()

    fun dateForPage(today: LocalDate, page: Int): LocalDate = today.plusDays((page - todayPageIndex).toLong())

    fun pageForDate(today: LocalDate, target: LocalDate): Int =
        (todayPageIndex + ChronoUnit.DAYS.between(today, target))
            .coerceIn(0L, (pageCount - 1).toLong())
            .toInt()
}
