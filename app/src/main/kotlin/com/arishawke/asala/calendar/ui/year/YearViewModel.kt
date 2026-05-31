/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.year

import java.time.LocalDate
import java.time.Year

class YearViewModel {
    companion object {
        // Load events for the visible year +/- radius years so the year
        // grid's mini-month dots render without reloading on every scroll
        // within a year. Half-open [start, endExclusive).
        fun yearFetchWindow(center: Year, radiusYears: Int): Pair<LocalDate, LocalDate> {
            val start = center.minusYears(radiusYears.toLong()).atDay(1)
            val endExclusive = center.plusYears(radiusYears.toLong() + 1).atDay(1)
            return start to endExclusive
        }
    }
}
