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
// duration are claimed before location so "at 3pm" is a time, not a place. all
// words come from the vocabulary resolved for the locale.
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
        val vocab = Vocabulary.forLocale(locale)
        val acc = Acc(input)
        extractDuration(acc, vocab)
        extractTimeRange(acc, vocab)
        if (acc.startTime == null) extractSingleTime(acc, vocab)
        extractDate(acc, now.toLocalDate(), locale, vocab)
        extractLocation(acc, vocab)

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
    // the am/pm comparison is English; a future language maps its meridiem here.
    private fun clock(hour: Int, minute: Int, meridiem: String?): LocalTime? {
        var h = hour
        when (meridiem?.lowercase()) {
            "am" -> if (h == 12) h = 0
            "pm" -> if (h != 12) h += 12
        }
        if (h !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(h, minute)
    }

    private fun extractDuration(acc: Acc, vocab: Vocabulary) {
        val units = alt(vocab.hourUnits + vocab.minuteUnits)
        val m = Regex(
            "\\b(?:${alt(vocab.forConnector)})\\s+(\\d+(?:\\.\\d+)?)\\s*($units)\\b",
            IC,
        ).find(acc.work) ?: return
        val n = m.groupValues[1].toDouble()
        val unit = m.groupValues[2].lowercase()
        acc.durationMin = if (unit in vocab.hourUnits) (n * 60).roundToInt() else n.roundToInt()
        acc.blank(m.range)
    }

    private fun extractTimeRange(acc: Acc, vocab: Vocabulary) {
        val mer = alt(vocab.meridiemAm + vocab.meridiemPm)
        val fromTo = alt(vocab.fromConnector + vocab.toConnector)
        val m = Regex(
            "(?:\\b(?:${alt(vocab.fromConnector)})\\s+)?\\b(\\d{1,2})(?::(\\d{2}))?\\s*($mer)?\\s*" +
                "(?:-|${alt(vocab.toConnector)})\\s*(\\d{1,2})(?::(\\d{2}))?\\s*($mer)?\\b",
            IC,
        ).find(acc.work) ?: return
        var sMer = m.groupValues[3].ifBlank { null }
        var eMer = m.groupValues[6].ifBlank { null }
        // a bare "N-M" with no meridiem and no from/to keyword is a number/date
        // range (e.g. "jan 3-15"), not a time; require a real time cue.
        if (sMer == null && eMer == null && !m.value.contains(Regex("\\b($fromTo)\\b", IC))) return
        if (sMer == null && eMer != null) sMer = eMer
        if (eMer == null && sMer != null) eMer = sMer
        val s = clock(m.groupValues[1].toInt(), m.groupValues[2].ifBlank { "0" }.toInt(), sMer) ?: return
        val e = clock(m.groupValues[4].toInt(), m.groupValues[5].ifBlank { "0" }.toInt(), eMer) ?: return
        acc.startTime = s
        acc.endTime = e
        acc.blank(m.range)
    }

    private fun extractSingleTime(acc: Acc, vocab: Vocabulary) {
        val at = alt(vocab.atConnector)
        val named = "${alt(vocab.noon)}|${alt(vocab.midnight)}"
        Regex("(?:\\b(?:$at)\\s+)?\\b($named)\\b", IC).find(acc.work)?.let { m ->
            val word = m.groupValues[1].lowercase()
            acc.startTime = if (word in vocab.noon) LocalTime.NOON else LocalTime.MIDNIGHT
            acc.blank(m.range)
            return
        }
        val mer = alt(vocab.meridiemAm + vocab.meridiemPm)
        Regex("(?:\\b(?:$at)\\s+)?\\b(\\d{1,2})(?::(\\d{2}))?\\s*($mer)\\b", IC).find(acc.work)?.let { m ->
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
        Regex("\\b(?:$at)\\s+(\\d{1,2})\\b", IC).find(acc.work)?.let { m ->
            val t = clock(m.groupValues[1].toInt(), 0, null)
            if (t != null) {
                acc.startTime = t
                acc.blank(m.range)
                return
            }
        }
    }

    private fun extractDate(acc: Acc, today: java.time.LocalDate, locale: Locale, vocab: Vocabulary) {
        val m = DateGrammar.find(acc.work, today, locale, vocab) ?: return
        acc.date = m.date
        acc.blank(m.range)
    }

    private fun extractLocation(acc: Acc, vocab: Vocabulary) {
        // time/date are already blanked, so any remaining "at X" is a place.
        val m = Regex("\\b(?:${alt(vocab.atConnector)})\\s+(.+)$", IC).find(acc.work) ?: return
        var loc = m.groupValues[1].replace(Regex("\\s+"), " ").trim()
        // drop a connector stranded by a blanked date/time ("... on <blanked>").
        loc = loc.replace(Regex("\\s+(${alt(vocab.locationTrimConnectors)})$", IC), "").trim()
        if (loc.isNotEmpty()) {
            acc.location = loc
            acc.blank(m.range)
        }
    }
}
