/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import java.util.Locale

// CalendarContract exposes no first-class birthday metadata. Birthday
// events surface via virtual calendars created by the Contacts Provider
// or by sync adapters; their distinguishing feature is the calendar's
// display name. Match a small set of locale keywords (case-insensitive
// substring) so the cake icon picks up the common "Birthdays" /
// "Birthdays and events" surfaces and the major non-English equivalents
// without needing per-locale detection logic.
object BirthdayDetection {
    // Substrings, not whole tokens, so plurals and compounds match too
    // ("Birthdays", "Geburtstage", "Aniversários", "Anniversaires de
    // mariage"). "anniversaire" intentionally also tags wedding-anniversary
    // calendars in French installs since the cake reads naturally there.
    private val keywords = setOf(
        "birthday",
        "geburtstag",
        "anniversaire",
        // ASCII variants alongside the diacritic forms so calendars typed
        // without tildes / accents (common on Latin American keyboards
        // and on some sync adapters that strip diacritics) still match.
        "cumpleaño",
        "cumpleano",
        "compleann",
        "verjaardag",
        "aniversári",
        "aniversari",
        "urodzin",
        "誕生日",
        "生日",
        "생일",
        "день рождения",
    )

    fun isBirthdayCalendar(displayName: String?): Boolean {
        if (displayName.isNullOrBlank()) return false
        val lower = displayName.lowercase(Locale.ROOT)
        return keywords.any { lower.contains(it) }
    }
}
