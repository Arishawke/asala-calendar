/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.widget

import java.time.LocalDate

object AgendaDayGrouping {
    // bounds the widget's view tree against a pathologically busy window. an
    // adapter-backed Glance LazyColumn raises the ceiling but does not remove it.
    const val MAX_EVENTS = 100

    // caps total event rows across sections, keeping the soonest events and
    // collapsing the trailing ones into the returned overflow count (drives a
    // "+N more" affordance). empty sections are not retained.
    fun cap(sections: List<AgendaDaySection>, maxEvents: Int = MAX_EVENTS): Pair<List<AgendaDaySection>, Int> {
        val total = sections.sumOf { it.events.size }
        if (total <= maxEvents) return sections to 0
        val capped = mutableListOf<AgendaDaySection>()
        var remaining = maxEvents
        for (section in sections) {
            if (remaining <= 0) break
            val taken = section.events.take(remaining)
            capped += section.copy(events = taken)
            remaining -= taken.size
        }
        return capped to (total - maxEvents)
    }

    // rows -> ordered day sections from today forward. an event still running
    // today (lastDate >= today) shows under today even if it began earlier;
    // only fully-past events (lastDate < today) drop. within a day, all-day
    // events sort before timed, then by start time.
    fun group(rows: List<AgendaEventRow>, today: LocalDate): List<AgendaDaySection> = rows
        .filter { !it.lastDate.isBefore(today) }
        .groupBy { maxOf(it.date, today) }
        .toSortedMap()
        .map { (date, events) ->
            AgendaDaySection(
                date,
                relativeDay(date, today),
                events.sortedWith(compareBy({ !it.allDay }, { it.startMillis })),
            )
        }

    private fun relativeDay(date: LocalDate, today: LocalDate): RelativeDay = when (date) {
        today -> RelativeDay.Today
        today.plusDays(1) -> RelativeDay.Tomorrow
        else -> RelativeDay.Other
    }
}
