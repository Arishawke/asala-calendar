/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import androidx.compose.runtime.Immutable

@Immutable
data class CalendarItem(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val visible: Boolean,
    // CalendarContract.Calendars.CAL_ACCESS_*; 500 = CAL_ACCESS_CONTRIBUTOR.
    // below this is read-only (subscriptions, foreign attendees, holidays).
    val accessLevel: Int,
    // local recolor override; authoritative on read so sync adapters can't
    // clobber the user's choice (CALENDAR_COLOR is also kept in sync).
    val overrideColor: Int? = null,
) {
    val isWritable: Boolean get() = accessLevel >= 500
    val displayColor: Int get() = overrideColor ?: color
}
