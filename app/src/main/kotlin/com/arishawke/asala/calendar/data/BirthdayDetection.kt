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

// CalendarContract has no birthday metadata; birthday calendars are only
// distinguishable by display name. Match locale keywords by case-insensitive
// substring rather than per-locale logic.
object BirthdayDetection {
    // substrings, not tokens, so plurals/compounds match. "anniversaire"
    // intentionally tags wedding-anniversary calendars in French installs.
    private val keywords = setOf(
        "birthday",
        "geburtstag",
        "anniversaire",
        // ascii variants for diacritic-stripping keyboards/sync adapters
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
