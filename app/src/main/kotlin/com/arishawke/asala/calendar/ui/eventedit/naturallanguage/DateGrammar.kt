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
import java.time.LocalDate
import java.util.Locale

internal data class DateMatch(val date: LocalDate, val range: IntRange)

// date extraction over a working string. patterns are tried longest/most-
// specific first so "next monday" is not grabbed as bare "monday". all words
// come from the vocabulary; the logic here is language-neutral.
internal object DateGrammar {
    // vocab-independent: compiled once, shared across every locale.
    private val NUMERIC_DATE = Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b")

    fun find(work: String, today: LocalDate, locale: Locale, vocab: Vocabulary = Vocabulary.English): DateMatch? {
        val grammar = CompiledGrammar.forVocabulary(vocab)
        relative(work, today, vocab, grammar)?.let { return it }
        nextThisWeekday(work, today, vocab, grammar)?.let { return it }
        bareWeekday(work, today, vocab, grammar)?.let { return it }
        monthNameDate(work, today, vocab, grammar)?.let { return it }
        numericDate(work, today, locale)?.let { return it }
        ordinalDate(work, today, grammar)?.let { return it }
        return null
    }

    private fun relative(work: String, today: LocalDate, vocab: Vocabulary, grammar: CompiledGrammar): DateMatch? {
        grammar.relativeToday.find(work)?.let { return DateMatch(today, it.range) }
        grammar.relativeTomorrow.find(work)?.let { return DateMatch(today.plusDays(1), it.range) }
        grammar.relativeIn.find(work)?.let { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return null
            val unit = m.groupValues[2].lowercase()
            // out-of-range counts overflow the date; fall through to title.
            val d = runCatching {
                if (unit in vocab.weekUnits) today.plusWeeks(n) else today.plusDays(n)
            }.getOrNull() ?: return null
            return DateMatch(d, m.range)
        }
        return null
    }

    private fun daysUntil(today: LocalDate, target: DayOfWeek): Long =
        ((target.value - today.dayOfWeek.value + 7) % 7).toLong() // 0 == today

    private fun nextThisWeekday(
        work: String,
        today: LocalDate,
        vocab: Vocabulary,
        grammar: CompiledGrammar,
    ): DateMatch? {
        val m = grammar.nextThisWeekday.find(work) ?: return null
        val target = vocab.weekdays.getValue(m.groupValues[2].lowercase())
        val upcoming = today.plusDays(daysUntil(today, target))
        val date = if (m.groupValues[1].lowercase() in vocab.nextQualifier) upcoming.plusDays(7) else upcoming
        return DateMatch(date, m.range)
    }

    private fun bareWeekday(work: String, today: LocalDate, vocab: Vocabulary, grammar: CompiledGrammar): DateMatch? {
        // "every monday" (also "every other monday", "every monday and tuesday") is
        // recurrence the quick-add cannot express; if an "every" qualifier appears
        // at all, decline a bare weekday rather than schedule a misleading one-off.
        if (grammar.everyQualifier.containsMatchIn(work)) return null
        return grammar.bareWeekday.find(work)?.let { m ->
            val target = vocab.weekdays.getValue(m.groupValues[1].lowercase())
            DateMatch(today.plusDays(daysUntil(today, target)), m.range)
        }
    }

    // construct a date, rolling a yearless past date forward a year. an invalid
    // day/month (e.g. feb 30) yields null so find() falls through to the title.
    private fun build(year: Int?, month: Int, day: Int, today: LocalDate): LocalDate? {
        val d = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull() ?: return null
        return if (year == null && d.isBefore(today)) d.plusYears(1) else d
    }

    private fun monthNameDate(work: String, today: LocalDate, vocab: Vocabulary, grammar: CompiledGrammar): DateMatch? {
        grammar.monthDayYear
            .find(work)?.let { m ->
                val d =
                    build(
                        m.groupValues[3].ifBlank {
                            null
                        }?.toInt(),
                        vocab.months.getValue(m.groupValues[1].lowercase()),
                        m.groupValues[2].toInt(),
                        today,
                    )
                if (d != null) return DateMatch(d, m.range)
            }
        grammar.dayMonthYear
            .find(work)?.let { m ->
                val d =
                    build(
                        m.groupValues[3].ifBlank {
                            null
                        }?.toInt(),
                        vocab.months.getValue(m.groupValues[2].lowercase()),
                        m.groupValues[1].toInt(),
                        today,
                    )
                if (d != null) return DateMatch(d, m.range)
            }
        return null
    }

    private fun isMonthFirst(locale: Locale): Boolean {
        val df = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, locale)
        val pattern = (df as? java.text.SimpleDateFormat)?.toPattern() ?: "M/d/yy"
        val mi = pattern.indexOf('M')
        val di = pattern.indexOf('d')
        return mi < 0 || di < 0 || mi < di
    }

    private fun numericDate(work: String, today: LocalDate, locale: Locale): DateMatch? {
        val m = NUMERIC_DATE.find(work) ?: return null
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        val year = m.groupValues[3].ifBlank { null }?.toInt()?.let { if (it < 100) 2000 + it else it }
        val monthFirst = isMonthFirst(locale)
        val d = build(year, if (monthFirst) a else b, if (monthFirst) b else a, today) ?: return null
        return DateMatch(d, m.range)
    }

    private fun ordinalDate(work: String, today: LocalDate, grammar: CompiledGrammar): DateMatch? {
        val m = grammar.ordinalDate.find(work) ?: return null
        val day = m.groupValues[1].toInt()
        val base = if (day >= today.dayOfMonth) today else today.plusMonths(1)
        if (day > base.lengthOfMonth()) return null
        return DateMatch(base.withDayOfMonth(day), m.range)
    }
}
