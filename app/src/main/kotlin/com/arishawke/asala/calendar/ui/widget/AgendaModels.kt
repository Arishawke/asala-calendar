/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.widget

import java.time.LocalDate

enum class RelativeDay { Today, Tomorrow, Other }

// instanceStartMillis is the instance BEGIN, used as the deep-link instance id
// (matches the notification path). date is the start day; lastDate is the
// inclusive last day the event covers (EventItem.lastDate), so an event that
// began before today but is still running surfaces under today instead of being
// dropped. both use the device zone with the same all-day/UTC handling.
data class AgendaEventRow(
    val eventId: Long,
    val instanceStartMillis: Long,
    val title: String,
    val startMillis: Long,
    val allDay: Boolean,
    val colorArgb: Int,
    val date: LocalDate,
    val lastDate: LocalDate,
)

data class AgendaDaySection(val date: LocalDate, val relativeDay: RelativeDay, val events: List<AgendaEventRow>)

enum class AgendaState { Loaded, NoPermission, NoCalendars, Empty }

data class AgendaSnapshot(val state: AgendaState, val sections: List<AgendaDaySection>)
