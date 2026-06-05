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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
    // The timed form is converted back into the event zone before taking the
    // date, so it round-trips the local end-date build() stored: a non-UTC zone's
    // end-of-day lands on a different UTC calendar day. zoneId defaults to UTC,
    // which leaves the all-day (date) form and UTC series unchanged.
    @Suppress("ReturnCount") // linear early-return chain over the UNTIL value forms
    fun untilDateOf(rrule: String?, zoneId: ZoneId = ZoneOffset.UTC): LocalDate? {
        val raw = partOf(rrule, "UNTIL=") ?: return null
        if (raw.contains('T')) {
            return runCatching {
                LocalDateTime.parse(raw, UTC_DATETIME_FORMAT)
                    .atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(zoneId)
                    .toLocalDate()
            }.getOrNull()
        }
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

    // true when the editor's recurrence fields exactly reproduce what this rule
    // was loaded as (see EventEditViewModel seeding), i.e. the user changed
    // nothing the editor models. Callers keep the original rule verbatim in that
    // case so tokens the editor cannot represent (a sub-day UNTIL time, BYDAY,
    // WKST) are not dropped, and a date-only UNTIL is not widened to ...235959Z
    // and made to regenerate a split-off occurrence.
    @Suppress("LongParameterList") // mirrors the editor's recurrence fields plus the event zone
    fun matchesEditorFields(
        rrule: String?,
        frequency: RecurrenceFrequency,
        interval: Int,
        untilDate: LocalDate?,
        count: Int?,
        zoneId: ZoneId = ZoneOffset.UTC,
    ): Boolean {
        val until = untilDateOf(rrule, zoneId)
        val effectiveCount = if (until != null) null else countOf(rrule)
        return frequencyOf(rrule) == frequency &&
            intervalOf(rrule) == interval &&
            until == untilDate &&
            effectiveCount == count
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
    @Suppress("LongParameterList") // recurrence fields plus the event zone for the UNTIL cutoff
    fun build(
        frequency: RecurrenceFrequency,
        interval: Int = 1,
        untilDate: LocalDate? = null,
        count: Int? = null,
        allDay: Boolean = false,
        zoneId: ZoneId = ZoneOffset.UTC,
    ): String {
        // RFC 5545 forbids UNTIL and COUNT in the same rule, but imported ICS /
        // CalDAV rows can carry both. Prefer UNTIL and drop COUNT rather than
        // throwing, so editing such an event can't crash the save.
        val effectiveCount = if (untilDate != null) null else count
        val parts = mutableListOf("FREQ=${frequency.name.uppercase()}")
        if (interval != 1) parts += "INTERVAL=$interval"
        if (effectiveCount != null) parts += "COUNT=$effectiveCount"
        if (untilDate != null) {
            parts += if (allDay) {
                "UNTIL=${untilDate.format(DATE_FORMAT)}"
            } else {
                // close the chosen day at the event zone's end-of-day, expressed
                // in UTC. a blanket T235959Z stamps a UTC time on a local date,
                // dropping a boundary-day occurrence in zones offset from UTC.
                // LocalTime.MAX is the last instant of the day; HHmmss formatting
                // drops sub-second precision, yielding the ...235959Z second.
                val cutoffUtc =
                    untilDate.atTime(LocalTime.MAX).atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC)
                "UNTIL=${cutoffUtc.format(UTC_DATETIME_FORMAT)}"
            }
        }
        return parts.joinToString(";")
    }

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
    private val UTC_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
}
