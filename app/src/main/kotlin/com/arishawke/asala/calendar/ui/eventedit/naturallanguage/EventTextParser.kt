/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit.naturallanguage

import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

// rule-based parser. each extractX claims its span from `work` (blanking it so
// the leftover becomes the title) and records its field. order matters: time and
// duration are claimed before location so "at 3pm" is a time, not a place.
object EventTextParser {

    private class Acc(var work: String) {
        var startTime: LocalTime? = null
        var endTime: LocalTime? = null
        var durationMin: Int? = null
        var date: java.time.LocalDate? = null
        var location: String? = null

        fun blank(range: IntRange) {
            work = work.replaceRange(range, " ".repeat(range.last - range.first + 1))
        }
    }

    fun parse(input: String, now: LocalDateTime, locale: Locale): ParsedEvent {
        val acc = Acc(input)
        extractDuration(acc)
        extractTimeRange(acc)
        if (acc.startTime == null) extractSingleTime(acc)
        extractDate(acc, now.toLocalDate(), locale)
        extractLocation(acc)

        val end = acc.endTime
            ?: acc.startTime?.let { s -> acc.durationMin?.let { s.plusMinutes(it.toLong()) } }
        val title = acc.work.replace(Regex("\\s+"), " ").trim()
        return ParsedEvent(
            title = title,
            location = acc.location,
            date = acc.date,
            startTime = acc.startTime,
            endTime = end,
        )
    }

    // --- helpers, filled in by later tasks ---
    private fun extractDuration(acc: Acc) {}
    private fun extractTimeRange(acc: Acc) {}
    private fun extractSingleTime(acc: Acc) {}
    private fun extractDate(acc: Acc, today: java.time.LocalDate, locale: Locale) {}
    private fun extractLocation(acc: Acc) {}
}
