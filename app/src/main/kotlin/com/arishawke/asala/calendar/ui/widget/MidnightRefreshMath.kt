/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.widget

import java.time.Instant
import java.time.ZoneId

object MidnightRefreshMath {
    // start of the next local day. atStartOfDay resolves DST gaps/overlaps.
    fun nextLocalMidnight(nowMillis: Long, zone: ZoneId): Long = Instant
        .ofEpochMilli(nowMillis)
        .atZone(zone)
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
}
