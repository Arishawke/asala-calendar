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

enum class OccasionKind { Birthday, Anniversary, None }

// CalendarContract has no occasion metadata; occasion calendars are only
// distinguishable by display name, so a user rename drops the classification
// (documented limitation, same as the cake icon).
object OccasionDetection {
    // substrings, not tokens, so plurals/compounds match. "anniversaire"
    // intentionally tags wedding-anniversary calendars in French installs.
    private val birthdayKeywords = setOf(
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

    // double-n "anniversar" distinguishes English anniversary/anniversaries
    // from the single-n Portuguese "aniversári(o)" birthday keyword above.
    private val anniversaryKeywords = setOf(
        "anniversar",
        "jubilä",
        "jubile",
    )

    fun classify(displayName: String?): OccasionKind {
        if (displayName.isNullOrBlank()) return OccasionKind.None
        val lower = displayName.lowercase(Locale.ROOT)
        return when {
            birthdayKeywords.any { lower.contains(it) } -> OccasionKind.Birthday
            anniversaryKeywords.any { lower.contains(it) } -> OccasionKind.Anniversary
            else -> OccasionKind.None
        }
    }
}
