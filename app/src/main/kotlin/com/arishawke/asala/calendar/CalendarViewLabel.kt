/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun CalendarView.label(): String = stringResource(
    when (this) {
        CalendarView.Year -> R.string.view_year
        CalendarView.Month -> R.string.view_month
        CalendarView.Week -> R.string.view_week
        CalendarView.ThreeDay -> R.string.view_three_day
        CalendarView.Day -> R.string.view_day
        CalendarView.Schedule -> R.string.view_schedule
    },
)
