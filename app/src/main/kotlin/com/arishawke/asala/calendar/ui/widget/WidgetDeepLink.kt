/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.widget

import com.arishawke.asala.calendar.CalendarView
import java.time.LocalDate

object WidgetDeepLink {
    // unknown / missing view defaults to Schedule (the day-tap target).
    fun parseView(name: String?): CalendarView =
        runCatching { CalendarView.valueOf(name ?: "") }.getOrDefault(CalendarView.Schedule)

    // decode the date deep-link extras; null when absent or the epoch-day is the
    // missing sentinel, so a malformed intent is dropped rather than mis-jumped.
    fun decode(present: Boolean, epochDay: Long, viewName: String?): Pair<LocalDate, CalendarView>? {
        if (!present || epochDay == Long.MIN_VALUE) return null
        return LocalDate.ofEpochDay(epochDay) to parseView(viewName)
    }
}
