/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

object MonthGridBuilder {
    private const val COLUMNS = 7
    const val MAX_CHIPS = 3

    private fun leadOffset(month: YearMonth, weekStart: DayOfWeek): Int =
        ((month.atDay(1).dayOfWeek.value - weekStart.value) + COLUMNS) % COLUMNS

    // first cell of the grid: back up from the 1st to the chosen week start.
    fun gridStart(month: YearMonth, weekStart: DayOfWeek): LocalDate =
        month.atDay(1).minusDays(leadOffset(month, weekStart).toLong())

    // total cells in the grid (4-6 weeks), week-start aware. the loader sizes its
    // event-load window to exactly this, so it never over- or under-fetches.
    fun gridDays(month: YearMonth, weekStart: DayOfWeek): Int {
        val rows = (leadOffset(month, weekStart) + month.lengthOfMonth() + COLUMNS - 1) / COLUMNS // ceil weeks
        return rows * COLUMNS
    }

    // full ordered list per date, all-day before timed then by start time. no cap here;
    // build() applies the MAX_CHIPS cap and computes moreCount.
    fun eventsByDate(events: List<MonthEvent>): Map<LocalDate, List<MonthCellEvent>> =
        events.groupBy { it.date }.mapValues { (_, list) ->
            list.sortedWith(compareBy({ !it.allDay }, { it.startMillis }))
                .map { MonthCellEvent(it.title, it.colorArgb) }
        }

    fun build(
        month: YearMonth,
        weekStart: DayOfWeek,
        today: LocalDate,
        eventsByDate: Map<LocalDate, List<MonthCellEvent>>,
    ): MonthGridData {
        val start = gridStart(month, weekStart)
        val cells = (0 until gridDays(month, weekStart)).map { i ->
            val date = start.plusDays(i.toLong())
            val day = eventsByDate[date].orEmpty()
            // reserve a slot for the "+N" row when overflow exists
            val shown = if (day.size > MAX_CHIPS) day.take(MAX_CHIPS - 1) else day
            MonthDayCell(
                date = date,
                inMonth = YearMonth.from(date) == month,
                isToday = date == today,
                events = shown,
                moreCount = day.size - shown.size,
            )
        }
        return MonthGridData(month, weekStart, cells.chunked(COLUMNS))
    }
}
