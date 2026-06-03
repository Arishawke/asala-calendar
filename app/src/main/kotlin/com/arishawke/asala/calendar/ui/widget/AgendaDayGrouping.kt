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
    // rows -> ordered day sections from today forward. within a day, all-day
    // events sort before timed, then by start time. past days are dropped.
    fun group(rows: List<AgendaEventRow>, today: LocalDate): List<AgendaDaySection> = rows
        .filter { !it.date.isBefore(today) }
        .sortedWith(compareBy({ it.date }, { !it.allDay }, { it.startMillis }))
        .groupBy { it.date }
        .map { (date, events) -> AgendaDaySection(date, relativeDay(date, today), events) }

    private fun relativeDay(date: LocalDate, today: LocalDate): RelativeDay = when (date) {
        today -> RelativeDay.Today
        today.plusDays(1) -> RelativeDay.Tomorrow
        else -> RelativeDay.Other
    }
}
