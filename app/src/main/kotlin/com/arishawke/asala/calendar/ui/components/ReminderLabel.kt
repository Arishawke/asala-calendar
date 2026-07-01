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
import com.arishawke.asala.calendar.data.TimeUnits

private const val MinutesPerDay = TimeUnits.HoursPerDay * TimeUnits.MinutesPerHour

// single source of truth for a reminder's "N before" label, shared by the editor
// picker and the detail sheet. snaps exact presets, else formats with the largest
// unit that divides cleanly (180 -> "3 hours before", 2880 -> "2 days before").
// null = no reminder; 0 = at the event's start.
@Composable
internal fun reminderLabel(m: Int?): String = when (m) {
    null -> stringResource(R.string.reminder_none)
    0 -> stringResource(R.string.reminder_at_time)
    TimeUnits.MinutesPerHour -> stringResource(R.string.reminder_one_hour)
    MinutesPerDay -> stringResource(R.string.reminder_one_day)
    else -> when {
        m % MinutesPerDay == 0 -> {
            val days = m / MinutesPerDay
            pluralStringResource(R.plurals.reminder_days_before, days, days)
        }
        m % TimeUnits.MinutesPerHour == 0 -> {
            val hours = m / TimeUnits.MinutesPerHour
            pluralStringResource(R.plurals.reminder_hours_before, hours, hours)
        }
        else -> pluralStringResource(R.plurals.reminder_minutes_before, m, m)
    }
}
