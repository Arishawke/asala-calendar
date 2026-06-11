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
}
