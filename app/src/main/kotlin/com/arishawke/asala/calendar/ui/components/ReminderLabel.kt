/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.arishawke.asala.calendar.R

private const val MinutesPerHour = 60
private const val MinutesPerDay = 24 * 60

// single source of truth for a reminder's "N before" label, shared by the editor
// picker and the detail sheet. snaps exact presets, else formats with the largest
// unit that divides cleanly (180 -> "3 hours before", 2880 -> "2 days before").
// null = no reminder; 0 = at the event's start.
@Composable
internal fun reminderLabel(m: Int?): String = when (m) {
    null -> stringResource(R.string.reminder_none)
    0 -> stringResource(R.string.reminder_at_time)
    MinutesPerHour -> stringResource(R.string.reminder_one_hour)
    MinutesPerDay -> stringResource(R.string.reminder_one_day)
    else -> when {
        m % MinutesPerDay == 0 -> {
            val days = m / MinutesPerDay
            pluralStringResource(R.plurals.reminder_days_before, days, days)
        }
        m % MinutesPerHour == 0 -> {
            val hours = m / MinutesPerHour
            pluralStringResource(R.plurals.reminder_hours_before, hours, hours)
        }
        else -> stringResource(R.string.reminder_minutes_before, m)
    }
}
