/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit.naturallanguage

import java.time.DayOfWeek
import java.util.Locale

// language-specific words for the rule-based parser, grouped by grammatical role
// rather than by source regex, so a translator fills roles. English is the only
// vocabulary today; forLocale is the seam a future language plugs into. known
// limits (ordinals, am/pm mapping, unescaped tokens) are recorded in ADR-0008.
internal data class Vocabulary(
    val weekdays: Map<String, DayOfWeek>,
    val bareHomographs: Set<String>,
    val months: Map<String, Int>,
    val today: List<String>,
    val tomorrow: List<String>,
    val nextQualifier: List<String>,
    val thisQualifier: List<String>,
    val inConnector: List<String>,
    val dayUnits: List<String>,
    val weekUnits: List<String>,
    val theArticle: List<String>,
    val ordinalSuffixes: List<String>,
    val atConnector: List<String>,
    val fromConnector: List<String>,
    val toConnector: List<String>,
    val forConnector: List<String>,
    val hourUnits: List<String>,
    val minuteUnits: List<String>,
    val noon: List<String>,
    val midnight: List<String>,
    val meridiemAm: List<String>,
    val meridiemPm: List<String>,
    val locationTrimConnectors: List<String>,
) {
    companion object {
        val English: Vocabulary = englishVocabulary()

        // seam: today every locale is English. a future language becomes one
        // branch here keyed on locale.language, touching no grammar code.
        fun forLocale(locale: Locale): Vocabulary = English
    }
}

private fun englishVocabulary(): Vocabulary {
    val weekdays = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY,
        "thu" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )
    val months = buildMap {
        val full = listOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
        )
        full.forEachIndexed { i, name ->
            put(name, i + 1)
            put(name.substring(0, 3), i + 1) // jan, feb, ...
        }
        put("sept", 9)
    }
    return Vocabulary(
        weekdays = weekdays,
        bareHomographs = setOf("sat", "sun", "wed"),
        months = months,
        today = listOf("today", "tonight"),
        tomorrow = listOf("tomorrow"),
        nextQualifier = listOf("next"),
        thisQualifier = listOf("this"),
        inConnector = listOf("in"),
        dayUnits = listOf("day", "days"),
        weekUnits = listOf("week", "weeks"),
        theArticle = listOf("the"),
        ordinalSuffixes = listOf("st", "nd", "rd", "th"),
        atConnector = listOf("at"),
        fromConnector = listOf("from"),
        toConnector = listOf("to"),
        forConnector = listOf("for"),
        hourUnits = listOf("h", "hr", "hrs", "hour", "hours"),
        minuteUnits = listOf("m", "min", "mins", "minute", "minutes"),
        noon = listOf("noon"),
        midnight = listOf("midnight"),
        meridiemAm = listOf("am"),
        meridiemPm = listOf("pm"),
        locationTrimConnectors = listOf("on", "from", "at"),
    )
}

// join tokens into a regex alternation, longest first so a specific token wins
// over a prefix ("monday" before "mon"). tokens are NOT escaped: every current
// token is plain ascii and escaping would interact with IGNORE_CASE. escaping is
// a future-language concern (see ADR-0008).
internal fun alt(tokens: Collection<String>): String =
    tokens.sortedByDescending { it.length }.joinToString("|")
