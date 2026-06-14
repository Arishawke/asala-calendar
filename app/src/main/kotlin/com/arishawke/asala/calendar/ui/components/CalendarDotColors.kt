/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.components

import com.arishawke.asala.calendar.data.EventItem

// one dot color per distinct calendar (first-seen order), capped, so a busy day
// on a single calendar stays single-dot. shared by the mini-month and year grids.
private const val MaxDotsPerCell = 3

internal fun distinctCalendarDotColors(events: List<EventItem>, max: Int = MaxDotsPerCell): List<Int> = events
    .groupBy { it.calendarId }
    .keys
    .take(max)
    .mapNotNull { calId -> events.firstOrNull { it.calendarId == calId }?.displayColor }
