/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

// CalendarContract.Events rows for recurring series store DURATION instead
// of DTEND; the per-occurrence end time has to be reconstructed from
// dtStart + parsed duration. This helper centralizes the fallback so the
// editor and detail sheet always see a usable end time even when DTEND
// itself is null.
object EventEndMillis {
    fun compute(dtStart: Long, dtEnd: Long?, durationIso8601: String?): Long {
        if (dtEnd != null) return dtEnd
        val durMs = EventDraft.parseIso8601DurationMs(durationIso8601)
        return if (durMs != null) dtStart + durMs else dtStart
    }
}
