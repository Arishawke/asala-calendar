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

        val visible = WidgetEventSource.visible(context)

        // null sections distinguishes "no calendars visible" from "no events",
        // and skips the event query when nothing is visible anyway.
        val sections = if (visible.calendars.all { it.id in visible.hidden }) {
            null
        } else {
            loadSections(context, visible, zone)
        }

        return when {
            sections == null -> AgendaSnapshot(AgendaState.NoCalendars, emptyList())
            sections.isEmpty() -> AgendaSnapshot(AgendaState.Empty, emptyList())
            else -> AgendaSnapshot(AgendaState.Loaded, sections)
        }
    }

    private suspend fun loadSections(
        context: Context,
        visible: WidgetEventSource.Visible,
        zone: ZoneId,
    ): List<AgendaDaySection> {
        val today = LocalDate.now(zone)
        val rows = WidgetEventSource
            .events(context, visible, today, today.plusDays(LOOKAHEAD_DAYS), zone)
            .map { e ->
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
