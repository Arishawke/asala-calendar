/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object RecurrenceExceptionMath {
    // RFC 5545 §3.3.10: UNTIL value type must match the series' DTSTART.
    // All-day parent: date-form UNTIL=YYYYMMDD of the day BEFORE the truncated
    // instance. Timed parent: datetime UNTIL=YYYYMMDDTHHMMSSZ one second before
    // the instance.
    fun untilUtcForTruncation(instanceUtcMillis: Long, allDay: Boolean = false): String {
        if (allDay) {
            val cutoff = Instant.ofEpochMilli(instanceUtcMillis)
                .atOffset(ZoneOffset.UTC)
                .toLocalDate()
                .minusDays(1)
            return "UNTIL=${cutoff.format(UTC_DATE_FORMAT)}"
        }
        val cutoff =
            Instant.ofEpochMilli(instanceUtcMillis - MILLIS_PER_SECOND)
                .atOffset(ZoneOffset.UTC)
        return "UNTIL=${cutoff.format(UTC_ICAL_FORMAT)}"
    }

    private const val MILLIS_PER_SECOND = 1000L

    /** Splice an `UNTIL=...` segment into an existing RRULE, replacing any
     *  existing UNTIL or COUNT.
     */
    fun appendUntil(rrule: String, untilSegment: String): String {
        val parts =
            rrule.split(";").filter {
                !it.startsWith("UNTIL=") && !it.startsWith("COUNT=")
            }
        return (parts + untilSegment).joinToString(";")
    }

    // "this and following" split: the future series carries only the
    // occurrences the truncated parent did not keep, so the total COUNT is
    // preserved (RFC 5545: COUNT bounds the rule from its own DTSTART, and the
    // split's DTSTART is the new anchor). UNTIL-bounded / open-ended rules have
    // no COUNT to over-generate and pass through unchanged. keptInstances is the
    // number of occurrences before the split point; never emit COUNT < 1.
    fun reduceSplitCount(rrule: String, keptInstances: Int): String = rrule.split(";").joinToString(";") { part ->
        if (!part.startsWith("COUNT=")) {
            part
        } else {
            val original = part.removePrefix("COUNT=").toIntOrNull() ?: return@joinToString part
            "COUNT=${(original - keptInstances).coerceAtLeast(1)}"
        }
    }

    // EXDATE value that excludes a single occurrence from the parent series:
    // timed -> UTC datetime, all-day -> date. Matches the value types
    // untilUtcForTruncation emits, so the provider recognizes the exclusion.
    fun exdateValue(instanceUtcMillis: Long, allDay: Boolean): String {
        val at = Instant.ofEpochMilli(instanceUtcMillis).atOffset(ZoneOffset.UTC)
        return at.format(if (allDay) UTC_DATE_FORMAT else UTC_ICAL_FORMAT)
    }

    // EXDATE accumulates across deletions: seed when empty, else comma-append so
    // a second exclusion does not drop the first. skip a value already present so
    // re-excluding the same slot stays idempotent and the field can't grow without
    // bound.
    fun mergeExdate(existing: String?, value: String): String {
        if (existing.isNullOrBlank()) return value
        return if (value in existing.split(",")) existing else "$existing,$value"
    }

    private val UTC_ICAL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
    private val UTC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
}
