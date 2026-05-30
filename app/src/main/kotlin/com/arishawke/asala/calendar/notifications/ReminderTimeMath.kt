/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

object ReminderTimeMath {
    // 9am local on the offset day matches the standard calendar-app
    // behavior; avoids the midnight surprise on all-day reminders.
    private const val ALL_DAY_ANCHOR_HOUR = 9

    fun computeAlarmTime(startMillis: Long, allDay: Boolean, minutesBefore: Int, zone: ZoneId): Long = if (allDay) {
        // CalendarContract stores all-day events at 00:00 UTC by convention.
        // Interpreting startMillis in the device zone rolls the date back a
        // day in negative-offset zones, so the date itself must come from
        // UTC. The 9am anchor still uses the device zone so the alarm fires
        // at local 9am on the offset date.
        val date = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val offsetDate = date.minusDays(minutesBefore / 1440L)
        offsetDate
            .atTime(ALL_DAY_ANCHOR_HOUR, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    } else {
        startMillis - minutesBefore * 60_000L
    }
}
