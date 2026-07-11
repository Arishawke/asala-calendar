/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

// pick the (eventId, originalMinutes) the snooze fires against. alertLookup may
// miss on a provider hiccup or when alertId is -1 (the original insert failed);
// null tells the caller to bail rather than schedule a phantom alarm.
internal object SnoozeResolution {
    fun resolve(
        alertId: Long,
        alertLookup: (Long) -> Pair<Long, Int>?,
        intentEventId: Long,
        intentOriginalMinutes: Int,
    ): Pair<Long, Int>? {
        val resolved = if (alertId > 0) alertLookup(alertId) else null
        val eventId = resolved?.first ?: intentEventId
        val originalMinutes = resolved?.second ?: intentOriginalMinutes
        if (eventId <= 0) return null
        return eventId to originalMinutes
    }
}
