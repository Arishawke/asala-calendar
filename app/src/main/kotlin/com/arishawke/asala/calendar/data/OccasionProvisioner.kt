/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import com.arishawke.asala.calendar.ui.settings.UserPreferences

// Okabe-Ito palette entries as raw ints; the data layer doesn't depend on ui.theme.
const val BIRTHDAYS_DEFAULT_COLOR: Int = 0xFFE69F00.toInt()
const val ANNIVERSARIES_DEFAULT_COLOR: Int = 0xFF56B4E9.toInt()

// turns the contact-occasions feature on and off: provisions/tears down the
// two local calendars that own the generated events, and drives the first sync.
class OccasionProvisioner(
    private val calendars: CalendarRepository,
    private val prefs: UserPreferences,
    private val sync: OccasionSync,
) {
    suspend fun enable(
        birthdaysName: String,
        anniversariesName: String,
        reminderMinutes: Int?,
        titleFor: (Occasion) -> String,
    ): Boolean {
        val birthdaysId = calendars.createLocalCalendar(birthdaysName, BIRTHDAYS_DEFAULT_COLOR)
        val anniversariesId = calendars.createLocalCalendar(anniversariesName, ANNIVERSARIES_DEFAULT_COLOR)
        if (birthdaysId == null || anniversariesId == null) {
            // roll back whichever half succeeded; don't leave an orphan calendar behind
            birthdaysId?.let { calendars.deleteLocalCalendar(it) }
            anniversariesId?.let { calendars.deleteLocalCalendar(it) }
            return false
        }

        prefs.setBirthdaysCalendarId(birthdaysId)
        prefs.setAnniversariesCalendarId(anniversariesId)
        prefs.setContactOccasionsEnabled(true)
        sync.sync(birthdaysId, anniversariesId, reminderMinutes, titleFor)
        return true
    }

    suspend fun disable(birthdaysCalendarId: Long?, anniversariesCalendarId: Long?) {
        if (birthdaysCalendarId != null && anniversariesCalendarId != null) {
            // drop reminder rows first so no orphan alarm survives the calendar delete
            sync.reapplyReminders(birthdaysCalendarId, anniversariesCalendarId, reminderMinutes = null)
        }
        birthdaysCalendarId?.let { calendars.deleteLocalCalendar(it) }
        anniversariesCalendarId?.let { calendars.deleteLocalCalendar(it) }

        prefs.setBirthdaysCalendarId(null)
        prefs.setAnniversariesCalendarId(null)
        prefs.setContactOccasionsEnabled(false)
    }
}
