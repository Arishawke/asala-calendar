/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.arishawke.asala.calendar.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

object MonthWidgetData {
    suspend fun load(
        context: Context,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MonthWidgetSnapshot {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return MonthWidgetSnapshot(MonthWidgetState.NoPermission, null)
        }

        val today = LocalDate.now(zone)
        val month = YearMonth.from(today)
        val visible = WidgetEventSource.visible(context)
        val weekStart = visible.prefs.weekStartsOn ?: WeekFields.of(Locale.getDefault()).firstDayOfWeek

        // load exactly the visible grid (4-6 weeks) so adjacent-month chips are correct.
        val start = MonthGridBuilder.gridStart(month, weekStart)
        val gridLast = start.plusDays((MonthGridBuilder.gridDays(month, weekStart) - 1).toLong())
        val endExclusive = gridLast.plusDays(1)
        val noTitle = context.getString(R.string.event_no_title)
        val events = WidgetEventSource.events(context, visible, start, endExclusive, zone)
            .mapNotNull { e ->
                val (firstCovered, lastCovered) = MonthGridBuilder.coveredRange(
                    e.startDate(zone), e.endDate(zone), e.allDay, start, gridLast,
                ) ?: return@mapNotNull null
                MonthEvent(
                    firstCovered = firstCovered,
                    lastCovered = lastCovered,
                    startMillis = e.startMillis,
                    allDay = e.allDay,
                    colorArgb = e.displayColor,
                    title = e.title.ifBlank { noTitle },
                )
            }

        // today stays real today; isToday cells appear only when month == current month.
        val grid = MonthGridBuilder.build(month, weekStart, today, MonthGridBuilder.eventsByDate(events, weekStart))
        return MonthWidgetSnapshot(MonthWidgetState.Ready, grid)
    }
}
