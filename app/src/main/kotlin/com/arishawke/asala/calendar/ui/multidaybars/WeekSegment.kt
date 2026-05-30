/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.multidaybars

import androidx.compose.runtime.Immutable

// One week-clipped slice of a multi-day all-day event. An event spanning
// several weeks produces one segment per week, each with its own startCol
// and endCol relative to the week. Continuation flags mark cut edges so
// the renderer can square the cut side and round the natural end.
@Immutable
data class WeekSegment(
    val eventId: Long,
    val title: String,
    val color: Int,
    val startCol: Int,
    val endCol: Int,
    val isContinuedLeft: Boolean,
    val isContinuedRight: Boolean,
    val lane: Int = UNASSIGNED_LANE,
    val isBirthday: Boolean = false,
) {
    val spanDays: Int get() = endCol - startCol + 1

    companion object {
        const val UNASSIGNED_LANE: Int = -1
    }
}
