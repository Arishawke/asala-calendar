/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

// recurring rows store DURATION not DTEND, so reconstruct the occurrence end
// from dtStart + parsed duration when DTEND is null.
object EventEndMillis {
    fun compute(dtStart: Long, dtEnd: Long?, durationIso8601: String?): Long {
        if (dtEnd != null) return dtEnd
        val durMs = EventDraft.parseIso8601DurationMs(durationIso8601)
        return if (durMs != null) dtStart + durMs else dtStart
    }
}
