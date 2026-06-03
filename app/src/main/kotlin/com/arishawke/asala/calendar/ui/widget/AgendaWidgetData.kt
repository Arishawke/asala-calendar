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
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.arishawke.asala.calendar.data.CalendarRepository
import com.arishawke.asala.calendar.data.EventRepository
import com.arishawke.asala.calendar.data.StorageModeFilter
import com.arishawke.asala.calendar.data.filteredAndRecolored
import com.arishawke.asala.calendar.drawerAccountKey
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import com.arishawke.asala.calendar.ui.settings.UserPrefs
import com.arishawke.asala.calendar.ui.settings.settingsDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

object AgendaWidgetData {
    private const val LOOKAHEAD_DAYS = 14L

    suspend fun load(context: Context, zone: ZoneId = ZoneId.systemDefault()): AgendaSnapshot {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return AgendaSnapshot(AgendaState.NoPermission, emptyList())
        }

        val cr = context.contentResolver
        val prefs = UserPreferences(context.settingsDataStore).prefs.first()
        val calendars = CalendarRepository(cr).calendars()

        // mirror AppViewModel.hiddenCalendarIdsFlow: explicit drawer hides +
        // account-key hides + storage-mode hides, all derived on read.
        val accountHidden = calendars
            .filter { drawerAccountKey(it.accountType, it.accountName) in prefs.drawerHiddenAccountKeys }
            .mapTo(mutableSetOf()) { it.id }
        val hidden = prefs.hiddenCalendarIds +
            accountHidden +
            StorageModeFilter.modeHiddenIds(prefs.storageMode, calendars)

        // null sections distinguishes "no calendars visible" from "no events",
        // and skips the event query when nothing is visible anyway.
        val sections = if (calendars.all { it.id in hidden }) {
            null
        } else {
            loadSections(cr, prefs, hidden, zone)
        }

        return when {
            sections == null -> AgendaSnapshot(AgendaState.NoCalendars, emptyList())
            sections.isEmpty() -> AgendaSnapshot(AgendaState.Empty, emptyList())
            else -> AgendaSnapshot(AgendaState.Loaded, sections)
        }
    }

    private suspend fun loadSections(
        cr: ContentResolver,
        prefs: UserPrefs,
        hidden: Set<Long>,
        zone: ZoneId,
    ): List<AgendaDaySection> {
        val today = LocalDate.now(zone)
        val events = EventRepository(cr)
            .observeEvents(startDate = today, endExclusive = today.plusDays(LOOKAHEAD_DAYS), zone = zone)
            .first()
            .filteredAndRecolored(hidden, prefs.calendarColorOverrides, prefs.eventColorOverrides)

        val rows = events.map { e ->
            AgendaEventRow(
                eventId = e.eventId,
                instanceStartMillis = e.startMillis,
                title = e.title,
                startMillis = e.startMillis,
                allDay = e.allDay,
                colorArgb = e.displayColor,
                date = e.startDate(zone),
            )
        }
        return AgendaDayGrouping.group(rows, today)
    }
}
