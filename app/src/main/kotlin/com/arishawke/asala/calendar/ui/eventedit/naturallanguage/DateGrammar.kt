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
// specific first so "next monday" is not grabbed as bare "monday".
internal object DateGrammar {
    private val IC = setOf(RegexOption.IGNORE_CASE)

    private val DOW: Map<String, DayOfWeek> = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY,
        "thu" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private val dowAlt = DOW.keys.sortedByDescending { it.length }.joinToString("|")

    fun find(work: String, today: LocalDate, locale: Locale): DateMatch? {
        relative(work, today)?.let { return it }
        nextThisWeekday(work, today)?.let { return it }
        bareWeekday(work, today)?.let { return it }
        monthNameDate(work, today)?.let { return it }
        numericDate(work, today, locale)?.let { return it }
        ordinalDate(work, today)?.let { return it }
        return null
    }

    private fun relative(work: String, today: LocalDate): DateMatch? {
        Regex("\\b(today|tonight)\\b", IC).find(work)?.let { return DateMatch(today, it.range) }
        Regex("\\btomorrow\\b", IC).find(work)?.let { return DateMatch(today.plusDays(1), it.range) }
        Regex("\\bin\\s+(\\d+)\\s+(day|days|week|weeks)\\b", IC).find(work)?.let { m ->
            val n = m.groupValues[1].toLong()
            val d = if (m.groupValues[2].startsWith("week", true)) today.plusWeeks(n) else today.plusDays(n)
            return DateMatch(d, m.range)
        }
        return null
    }

    private fun daysUntil(today: LocalDate, target: DayOfWeek): Long =
        ((target.value - today.dayOfWeek.value + 7) % 7).toLong() // 0 == today

    private fun nextThisWeekday(work: String, today: LocalDate): DateMatch? {
        val m = Regex("\\b(next|this)\\s+($dowAlt)\\b", IC).find(work) ?: return null
        val target = DOW.getValue(m.groupValues[2].lowercase())
        val upcoming = today.plusDays(daysUntil(today, target))
        val date = if (m.groupValues[1].equals("next", true)) upcoming.plusDays(7) else upcoming
        return DateMatch(date, m.range)
    }

    private fun bareWeekday(work: String, today: LocalDate): DateMatch? {
        val m = Regex("\\b($dowAlt)\\b", IC).find(work) ?: return null
        val target = DOW.getValue(m.groupValues[1].lowercase())
        return DateMatch(today.plusDays(daysUntil(today, target)), m.range)
    }

    private val MONTHS: Map<String, Int> = buildMap {
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
    private val monAlt = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")

    // construct a date, rolling a yearless past date forward a year. an invalid
    // day/month (e.g. feb 30) yields null so find() falls through to the title.
    private fun build(year: Int?, month: Int, day: Int, today: LocalDate): LocalDate? {
        val d = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull() ?: return null
        return if (year == null && d.isBefore(today)) d.plusYears(1) else d
    }

    private fun monthNameDate(work: String, today: LocalDate): DateMatch? {
        Regex("\\b($monAlt)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b", IC)
            .find(work)?.let { m ->
                val d =
                    build(
                        m.groupValues[3].ifBlank {
                            null
                        }?.toInt(),
                        MONTHS.getValue(m.groupValues[1].lowercase()),
                        m.groupValues[2].toInt(),
                        today,
                    )
                if (d != null) return DateMatch(d, m.range)
            }
        Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+($monAlt)\\.?(?:,?\\s+(\\d{4}))?\\b", IC)
            .find(work)?.let { m ->
                val d =
                    build(
                        m.groupValues[3].ifBlank {
                            null
                        }?.toInt(),
                        MONTHS.getValue(m.groupValues[2].lowercase()),
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
        val m = Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b").find(work) ?: return null
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        val year = m.groupValues[3].ifBlank { null }?.toInt()?.let { if (it < 100) 2000 + it else it }
        val monthFirst = isMonthFirst(locale)
        val d = build(year, if (monthFirst) a else b, if (monthFirst) b else a, today) ?: return null
        return DateMatch(d, m.range)
    }

    private fun ordinalDate(work: String, today: LocalDate): DateMatch? {
        val m = Regex("\\b(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)\\b", IC).find(work) ?: return null
        val day = m.groupValues[1].toInt()
        val base = if (day >= today.dayOfMonth) today else today.plusMonths(1)
        if (day > base.lengthOfMonth()) return null
        return DateMatch(base.withDayOfMonth(day), m.range)
    }
}
