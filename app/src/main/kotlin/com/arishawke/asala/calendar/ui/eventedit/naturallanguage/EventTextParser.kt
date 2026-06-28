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
        if (acc.startTime == null) extractTimeOfDay(acc, vocab)
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
        val sMer = m.groupValues[3].ifBlank { null }
        val eMer = m.groupValues[6].ifBlank { null }
        val times = if (sMer == null && eMer == null) {
            // a bare "N-M" is a number/date range ("jan 3-15") unless a from/to cue
            // signals time intent ("9 to 5"), then it reads as a daytime range.
            if (m.value.contains(Regex("\\b($fromTo)\\b", IC))) {
                daytimeRange(
                    m.groupValues[1].toInt(),
                    m.groupValues[2].ifBlank { "0" }.toInt(),
                    m.groupValues[4].toInt(),
                    m.groupValues[5].ifBlank { "0" }.toInt(),
                )
            } else {
                null
            }
        } else {
            inheritedRange(m, sMer, eMer)
        }
        times?.let {
            acc.startTime = it.first
            acc.endTime = it.second
            acc.blank(m.range)
        }
    }

    // a meridiem-less range read as daytime: the start biased to working hours
    // (early hours are afternoon, late morning stays morning, 12 is noon), the end
    // the first reading strictly after it. null if no sane reading fits.
    private fun daytimeRange(sh: Int, sm: Int, eh: Int, em: Int): Pair<LocalTime, LocalTime>? = runCatching {
        val ends = if (eh in 0..23) listOf(eh % 12, eh % 12 + 12).map { LocalTime.of(it, em) } else emptyList()
        // bias an early start hour into the afternoon, but fall back to the literal
        // hour when nothing reads after it (so "6 to 12" is 06:00-12:00, not blank).
        val biased = LocalTime.of(if (sh in 1..6) sh + 12 else sh, sm)
        val start = if (ends.any { it.isAfter(biased) }) biased else LocalTime.of(sh, sm)
        ends.firstOrNull { it.isAfter(start) }?.let { start to it }
    }.getOrNull()

    // inherit the present meridiem onto the bare side; if that inverts the range,
    // flip the originally-bare side to the other half of the day ("9-5pm" is
    // 9am-5pm). an explicit both-sided overnight ("10pm to 2am") is left alone.
    private fun inheritedRange(m: MatchResult, sMer: String?, eMer: String?): Pair<LocalTime, LocalTime>? {
        fun flip(mer: String?) = if (mer.equals("pm", ignoreCase = true)) "am" else "pm"
        val sh = m.groupValues[1].toInt()
        val sm = m.groupValues[2].ifBlank { "0" }.toInt()
        val eh = m.groupValues[4].toInt()
        val em = m.groupValues[5].ifBlank { "0" }.toInt()
        val s0 = clock(sh, sm, sMer ?: eMer)
        val e0 = clock(eh, em, eMer ?: sMer)
        return when {
            s0 == null || e0 == null -> null
            e0.isAfter(s0) -> s0 to e0
            sMer == null -> (clock(sh, sm, flip(eMer)) ?: s0) to e0
            eMer == null -> s0 to (clock(eh, em, flip(sMer)) ?: e0)
            else -> s0 to e0
        }
    }

    private fun extractSingleTime(acc: Acc, vocab: Vocabulary) {
        val at = alt(vocab.atConnector)
        val named = alt(vocab.noon + vocab.midnight)
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

    private fun extractTimeOfDay(acc: Acc, vocab: Vocabulary) {
        if (vocab.timeOfDay.isEmpty()) return
        // "tonight" reads as evening and stays in the text so the date grammar
        // resolves it to today.
        val tonight = Regex("\\b(${alt(vocab.tonight)})\\b", IC).find(acc.work)
        // the other words set a time only after a temporal lead (this/the or a day
        // word), so a plain title like "movie night" keeps its word untouched.
        val lead = alt(vocab.thisQualifier + vocab.theArticle + vocab.today + vocab.tomorrow + vocab.weekdays.keys)
        val words = alt(vocab.timeOfDay.keys - vocab.tonight.toSet())
        val led = Regex("\\b($lead)\\s+($words)\\b", IC).find(acc.work)
        if (tonight != null) {
            acc.startTime = vocab.timeOfDay[tonight.groupValues[1].lowercase()]
        } else if (led != null) {
            acc.startTime = vocab.timeOfDay[led.groupValues[2].lowercase()]
            // keep a day-word lead for the date grammar; blank a this/the qualifier
            // together with the time word so neither leaks into the title.
            val leadWord = led.groupValues[1].lowercase()
            val dayLead = leadWord in vocab.today || leadWord in vocab.tomorrow || leadWord in vocab.weekdays
            val from = if (dayLead) led.range.last - led.groupValues[2].length + 1 else led.range.first
            acc.blank(from..led.range.last)
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
