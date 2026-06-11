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
import kotlin.math.roundToInt

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

    private val IC = setOf(RegexOption.IGNORE_CASE)

    // build a LocalTime from clock parts, applying am/pm. null if out of range.
    private fun clock(hour: Int, minute: Int, meridiem: String?): LocalTime? {
        var h = hour
        when (meridiem?.lowercase()) {
            "am" -> if (h == 12) h = 0
            "pm" -> if (h != 12) h += 12
        }
        if (h !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(h, minute)
    }

    private fun extractDuration(acc: Acc) {
        val m = Regex(
            "\\bfor\\s+(\\d+(?:\\.\\d+)?)\\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes)\\b",
            IC,
        ).find(acc.work) ?: return
        val n = m.groupValues[1].toDouble()
        acc.durationMin = if (m.groupValues[2].lowercase().startsWith("h")) (n * 60).roundToInt() else n.roundToInt()
        acc.blank(m.range)
    }

    private fun extractTimeRange(acc: Acc) {
        val m = Regex(
            "(?:\\bfrom\\s+)?\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\s*(?:-|to)\\s*" +
                "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b",
            IC,
        ).find(acc.work) ?: return
        var sMer = m.groupValues[3].ifBlank { null }
        var eMer = m.groupValues[6].ifBlank { null }
        // a bare "N-M" with no meridiem and no from/to keyword is a number/date
        // range (e.g. "jan 3-15"), not a time; require a real time cue.
        if (sMer == null && eMer == null && !m.value.contains(Regex("\\b(from|to)\\b", IC))) return
        if (sMer == null && eMer != null) sMer = eMer
        if (eMer == null && sMer != null) eMer = sMer
        val s = clock(m.groupValues[1].toInt(), m.groupValues[2].ifBlank { "0" }.toInt(), sMer) ?: return
        val e = clock(m.groupValues[4].toInt(), m.groupValues[5].ifBlank { "0" }.toInt(), eMer) ?: return
        acc.startTime = s
        acc.endTime = e
        acc.blank(m.range)
    }

    private fun extractSingleTime(acc: Acc) {
        Regex("(?:\\bat\\s+)?\\b(noon|midnight)\\b", IC).find(acc.work)?.let { m ->
            acc.startTime = if (m.groupValues[1].equals("noon", true)) LocalTime.NOON else LocalTime.MIDNIGHT
            acc.blank(m.range)
            return
        }
        Regex("(?:\\bat\\s+)?\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b", IC).find(acc.work)?.let { m ->
            val t = clock(m.groupValues[1].toInt(), m.groupValues[2].ifBlank { "0" }.toInt(), m.groupValues[3])
            if (t != null) {
                acc.startTime = t
                acc.blank(m.range)
                return
            }
        }
        Regex("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").find(acc.work)?.let { m ->
            acc.startTime = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            acc.blank(m.range)
            return
        }
        Regex("\\bat\\s+(\\d{1,2})\\b", IC).find(acc.work)?.let { m ->
            val t = clock(m.groupValues[1].toInt(), 0, null)
            if (t != null) {
                acc.startTime = t
                acc.blank(m.range)
                return
            }
        }
    }

    private fun extractDate(acc: Acc, today: java.time.LocalDate, locale: Locale) {}
    private fun extractLocation(acc: Acc) {}
}
