/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

// Single source of truth for wall-clock unit conversions used across the
// timeline, schedule, settings, and reminder code.
internal object TimeUnits {
    const val MinutesPerHour = 60
    const val MillisPerMinute = 60_000L
    const val HoursPerDay = 24
    const val MaxStartHour = HoursPerDay - 1
}
