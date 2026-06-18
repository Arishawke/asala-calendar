/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.multidaybars

// event ids whose assigned lane sits at or beyond the visible cap. MultiDayBarRow
// drops these segments, so the row must surface them through a "+N more" affordance.
internal fun overflowEventIds(segments: List<WeekSegment>, maxLanes: Int): Set<Long> =
    segments.filterTo(mutableSetOf()) { it.lane >= maxLanes }.mapTo(mutableSetOf()) { it.eventId }
