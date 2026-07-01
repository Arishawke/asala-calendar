/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

data class ParsedOccasionDate(val month: Int, val day: Int, val year: Int?)

// contacts store birthdays/anniversaries as CommonDataKinds.Event START_DATE:
// "YYYY-MM-DD" or, when the year is unknown, the RFC 2426 no-year form "--MM-DD".
object OccasionDateParser {
    fun parse(raw: String?): ParsedOccasionDate? {
        val match = raw?.let { DATE_PATTERN.find(it) } ?: return null
        val month = match.groupValues[MONTH_GROUP].toInt()
        val day = match.groupValues[DAY_GROUP].toInt()
        val year = match.groups[YEAR_GROUP]?.value?.toInt()
        return ParsedOccasionDate(month, day, year).takeIf { isValidMonthDay(month, day) }
    }

    // Feb 29 is accepted regardless of year: leap birthdays are legal and the
    // no-year form has no year to check against, so leap-validity is deferred
    // to yearly recurrence expansion.
    private fun isValidMonthDay(month: Int, day: Int): Boolean {
        if (month < MIN_MONTH || month > MAX_MONTH) return false
        return day in MIN_DAY..maxDayOf(month)
    }

    private fun maxDayOf(month: Int): Int = when (month) {
        FEBRUARY -> FEBRUARY_MAX_DAY
        APRIL, JUNE, SEPTEMBER, NOVEMBER -> SHORT_MONTH_MAX_DAY
        else -> LONG_MONTH_MAX_DAY
    }

    private const val YEAR_GROUP = 1
    private const val MONTH_GROUP = 2
    private const val DAY_GROUP = 3
    private const val MIN_MONTH = 1
    private const val MAX_MONTH = 12
    private const val MIN_DAY = 1
    private const val FEBRUARY = 2
    private const val APRIL = 4
    private const val JUNE = 6
    private const val SEPTEMBER = 9
    private const val NOVEMBER = 11
    private const val FEBRUARY_MAX_DAY = 29
    private const val SHORT_MONTH_MAX_DAY = 30
    private const val LONG_MONTH_MAX_DAY = 31

    // year group is non-participating for the no-year "--MM-DD" branch, so
    // match.groups[YEAR_GROUP] is null rather than an empty string there.
    private val DATE_PATTERN = Regex("""^(?:(\d{4})-|--)(\d{2})-(\d{2})$""")
}
