/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.widget

import android.content.Context
import com.arishawke.asala.calendar.computeHiddenCalendarIds
import com.arishawke.asala.calendar.data.CalendarItem
import com.arishawke.asala.calendar.data.CalendarRepository
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.data.EventRepository
import com.arishawke.asala.calendar.data.filteredAndRecolored
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import com.arishawke.asala.calendar.ui.settings.UserPrefs
import com.arishawke.asala.calendar.ui.settings.settingsDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

// single source of truth for widget visibility + event load, shared by the
// agenda and month widgets. visibility goes through computeHiddenCalendarIds so
// the widgets and the in-app views resolve the hidden set identically.
object WidgetEventSource {
    data class Visible(val prefs: UserPrefs, val calendars: List<CalendarItem>, val hidden: Set<Long>)

    suspend fun visible(context: Context): Visible {
        val prefs = UserPreferences(context.settingsDataStore).prefs.first()
        val calendars = CalendarRepository(context.contentResolver).calendars()
        val hidden = computeHiddenCalendarIds(
            hiddenCalendarIds = prefs.hiddenCalendarIds,
            drawerHiddenAccountKeys = prefs.drawerHiddenAccountKeys,
            storageMode = prefs.storageMode,
            calendars = calendars,
        )
        return Visible(prefs, calendars, hidden)
    }

    suspend fun events(
        context: Context,
        visible: Visible,
        startDate: LocalDate,
        endExclusive: LocalDate,
        zone: ZoneId,
    ): List<EventItem> = EventRepository(context.contentResolver)
        .observeEvents(startDate = startDate, endExclusive = endExclusive, zone = zone)
        .first()
        .filteredAndRecolored(
            visible.hidden,
            visible.prefs.calendarColorOverrides,
            visible.prefs.eventColorOverrides,
        )
}
