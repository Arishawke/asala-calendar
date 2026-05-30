/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

// Decide what (eventId, originalMinutes) the snooze should fire against
// given the alertId received in the action intent. alertLookup queries the
// CalendarAlerts row; it may be unavailable on a provider hiccup or when
// alertId is -1 (the original ensureCalendarAlert insert failed). Returning
// null tells the caller to bail rather than schedule a phantom alarm.
internal object SnoozeResolution {
    fun resolve(alertId: Long, alertLookup: (Long) -> Pair<Long, Int>?, intentEventId: Long): Pair<Long, Int>? {
        val resolved = if (alertId > 0) alertLookup(alertId) else null
        val eventId = resolved?.first ?: intentEventId
        val originalMinutes = resolved?.second ?: 0
        if (eventId <= 0) return null
        return eventId to originalMinutes
    }
}
