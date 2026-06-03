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

    // grid-clamped covered day range for an event occupying [firstDate, lastDate]
    // (use EventItem.startDate / lastDate), or null if it does not intersect the
    // grid. all-day exclusivity and the midnight-end rule live in EventItem.lastDate.
    fun coveredRange(
        firstDate: LocalDate,
        lastDate: LocalDate,
        gridStart: LocalDate,
        gridLast: LocalDate,
    ): Pair<LocalDate, LocalDate>? {
        val firstCovered = maxOf(firstDate, gridStart)
        val lastCovered = minOf(lastDate, gridLast)
        return if (firstCovered > lastCovered) null else firstCovered to lastCovered
    }

    // expand each event across the days it covers, tagging label vs strip, then
    // order each day's slice bands-first (multi-day before single-day) so the
    // build() cap keeps bands visible. no cap here; build() applies MAX_CHIPS.
    fun eventsByDate(
        events: List<MonthEvent>,
        weekStart: DayOfWeek,
    ): Map<LocalDate, List<MonthCellEvent>> =
        events.flatMap { it.expand(weekStart) }
            .groupBy { it.date }
            .mapValues { (_, dayEntries) ->
                val (bands, single) = dayEntries.partition { it.multiDay }
                (
                    bands.sortedWith(compareBy({ it.firstCovered }, { it.startMillis })) +
                        single.sortedWith(compareBy({ !it.allDay }, { it.startMillis }))
                    ).map { MonthCellEvent(it.title, it.colorArgb, it.isLabel) }
            }

    // one event's slice on one covered day, carrying the keys eventsByDate orders by.
    private data class DayEntry(
        val date: LocalDate,
        val multiDay: Boolean,
        val firstCovered: LocalDate,
        val allDay: Boolean,
        val startMillis: Long,
        val title: String,
        val colorArgb: Int,
        val isLabel: Boolean,
    )

    private fun MonthEvent.expand(weekStart: DayOfWeek): List<DayEntry> {
        val multiDay = lastCovered > firstCovered
        return generateSequence(firstCovered) { d -> if (d < lastCovered) d.plusDays(1) else null }
            .map { d ->
                DayEntry(
                    date = d,
                    multiDay = multiDay,
                    firstCovered = firstCovered,
                    allDay = allDay,
                    startMillis = startMillis,
                    title = title,
                    colorArgb = colorArgb,
                    isLabel = d == firstCovered || d.dayOfWeek == weekStart,
                )
            }
            .toList()
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
