/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.ui.theme.AsalaCalendarTheme
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import java.time.LocalDate

// Previews compose the badge + cell stack so the visual matches what
// MonthGrid renders. The bar row is omitted because previews are about
// per-cell rendering, not the full week layout.
@Preview(name = "Today, light")
@Preview(name = "Today, dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DayCellTodayPreview() {
    AsalaCalendarTheme(dynamicColor = false) {
        val day = CalendarDay(LocalDate.of(2026, 5, 21), DayPosition.MonthDate)
        Column(modifier = Modifier.size(80.dp, 120.dp)) {
            DayNumberBadge(day = day, isToday = true)
            DayCell(
                day = day,
                events = listOf(
                    sampleEvent(1, "Coffee with Sam", 0xFF1A73E8.toInt()),
                    sampleEvent(2, "Standup", 0xFF34A853.toInt()),
                ),
            )
        }
    }
}

@Preview(name = "Out of month, light")
@Preview(name = "Out of month, dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DayCellOutOfMonthPreview() {
    AsalaCalendarTheme(dynamicColor = false) {
        val day = CalendarDay(LocalDate.of(2026, 4, 30), DayPosition.OutDate)
        Column(modifier = Modifier.size(80.dp, 120.dp)) {
            DayNumberBadge(day = day, isToday = false)
            DayCell(day = day, events = emptyList())
        }
    }
}

@Preview(name = "Overflow, light")
@Preview(name = "Overflow, dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DayCellOverflowPreview() {
    AsalaCalendarTheme(dynamicColor = false) {
        val day = CalendarDay(LocalDate.of(2026, 5, 22), DayPosition.MonthDate)
        Column(modifier = Modifier.size(80.dp, 120.dp)) {
            DayNumberBadge(day = day, isToday = false)
            DayCell(
                day = day,
                events = listOf(
                    sampleEvent(1, "Standup", 0xFF1A73E8.toInt()),
                    sampleEvent(2, "1:1 Sam", 0xFF34A853.toInt()),
                    sampleEvent(3, "Design rvw", 0xFFEA4335.toInt()),
                    sampleEvent(4, "Coffee", 0xFFFBBC04.toInt()),
                    sampleEvent(5, "Office hrs", 0xFF1A73E8.toInt()),
                    sampleEvent(6, "Demo prep", 0xFF34A853.toInt()),
                ),
                onOverflowClick = {},
            )
        }
    }
}

private fun sampleEvent(id: Long, title: String, color: Int) = EventItem(
    instanceId = id,
    eventId = id,
    calendarId = 1L,
    title = title,
    startMillis = 0L,
    endMillis = 0L,
    allDay = false,
    displayColor = color,
)
