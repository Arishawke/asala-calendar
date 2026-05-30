/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import java.time.LocalDate
import java.time.ZoneId

// Maps a date range to the UTC millis boundaries CalendarContract expects.
// Honors the supplied zone, so DST forward/backward days yield 23h / 25h
// spans rather than a fixed 24h.
internal fun dayRangeMillis(startDate: LocalDate, endExclusive: LocalDate, zone: ZoneId): Pair<Long, Long> {
    val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
    return startMillis to endMillis
}
