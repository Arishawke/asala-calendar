/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import java.time.LocalDate
import java.util.Locale

enum class RecurrenceFrequency { Daily, Weekly, Monthly, Yearly }

object RecurrenceRule {
    fun frequencyOf(rrule: String?): RecurrenceFrequency? {
        if (rrule.isNullOrBlank()) return null
        val freq = partOf(rrule, "FREQ=") ?: return null
        return when (freq) {
            "DAILY" -> RecurrenceFrequency.Daily
            "WEEKLY" -> RecurrenceFrequency.Weekly
            "MONTHLY" -> RecurrenceFrequency.Monthly
            "YEARLY" -> RecurrenceFrequency.Yearly
            else -> null
        }
    }

    fun intervalOf(rrule: String?): Int = partOf(rrule, "INTERVAL=")?.toIntOrNull()?.takeIf { it > 0 } ?: 1

    fun countOf(rrule: String?): Int? = partOf(rrule, "COUNT=")?.toIntOrNull()?.takeIf { it > 0 }

    // RFC 5545 UNTIL is either YYYYMMDD (all-day) or YYYYMMDDTHHMMSSZ (timed UTC).
    // We round-trip only the date portion; the time portion drives the cutoff
    // boundary at build time.
    fun untilDateOf(rrule: String?): LocalDate? {
        val raw = partOf(rrule, "UNTIL=") ?: return null
        val dateStr = raw.take(8)
        if (dateStr.length != 8) return null
        return runCatching {
            LocalDate.of(
                dateStr.substring(0, 4).toInt(),
                dateStr.substring(4, 6).toInt(),
                dateStr.substring(6, 8).toInt(),
            )
        }.getOrNull()
    }

    private fun partOf(rrule: String?, prefix: String): String? {
        if (rrule.isNullOrBlank()) return null
        return rrule
            .split(";")
            .firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
    }

    // RFC 5545 §3.3.10: UNTIL value type must match DTSTART. Date-form for
    // all-day (DTSTART is DATE), datetime-UTC for timed (DTSTART is DATE-TIME).
    // CalDAV-compliant servers reject the wrong shape; Locale.ROOT pins ASCII
    // digits so non-Latin locales don't corrupt the wire format.
    fun build(
        frequency: RecurrenceFrequency,
        interval: Int = 1,
        untilUtc: LocalDate? = null,
        count: Int? = null,
        allDay: Boolean = false,
    ): String {
        // RFC 5545 forbids UNTIL and COUNT in the same rule, but imported ICS /
        // CalDAV rows can carry both. Prefer UNTIL and drop COUNT rather than
        // throwing, so editing such an event can't crash the save.
        val effectiveCount = if (untilUtc != null) null else count
        val parts = mutableListOf("FREQ=${frequency.name.uppercase()}")
        if (interval != 1) parts += "INTERVAL=$interval"
        if (effectiveCount != null) parts += "COUNT=$effectiveCount"
        if (untilUtc != null) {
            val ymd = String.format(
                Locale.ROOT,
                "%04d%02d%02d",
                untilUtc.year,
                untilUtc.monthValue,
                untilUtc.dayOfMonth,
            )
            parts += if (allDay) "UNTIL=$ymd" else "UNTIL=${ymd}T235959Z"
        }
        return parts.joinToString(";")
    }
}
