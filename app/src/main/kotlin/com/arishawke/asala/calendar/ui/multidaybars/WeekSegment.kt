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
import com.arishawke.asala.calendar.data.OccasionKind

// one week-clipped slice of a multi-day all-day event (one per week spanned); continuation flags mark cut edges
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
    val occasion: OccasionKind = OccasionKind.None,
    val occasionName: String? = null,
    // parent event's original DTSTART, for computing age from the occurrence year.
    val parentDtStartMillis: Long = 0L,
    val occurrenceStartMillis: Long = 0L,
) {
    val spanDays: Int get() = endCol - startCol + 1

    companion object {
        const val UNASSIGNED_LANE: Int = -1
    }
}
