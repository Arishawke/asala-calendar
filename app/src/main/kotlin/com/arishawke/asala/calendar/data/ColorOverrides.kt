/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

// single source of the chip/accent color precedence: event override >
// calendar override > fallback. synced calendars never persist overrides to
// the provider, so every render path resolves them here.
fun resolveOverrideColor(
    eventId: Long,
    calendarId: Long,
    fallback: Int,
    eventOverrides: Map<Long, Int>,
    calendarOverrides: Map<Long, Int>,
): Int = eventOverrides[eventId]
    ?: calendarOverrides[calendarId]
    ?: fallback
