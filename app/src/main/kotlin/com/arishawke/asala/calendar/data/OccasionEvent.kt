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
import java.time.ZoneOffset

// no real year on record: DTSTART still needs one for the yearly-recurring
// all-day event, so a fixed placeholder year stands in (never rendered).
const val OCCASION_NO_YEAR_SENTINEL = 1604

const val OCCASION_URI_PREFIX = "asala://occasion/"

private val OCCASION_URI_RE = Regex("^asala://occasion/(\\d+)/(\\w+)$")

// identity for app-owned occasion events (ADR-0002 rule 7: no sync-adapter
// writes, so CUSTOM_APP_PACKAGE/CUSTOM_APP_URI stand in for _SYNC_ID).
fun occasionCustomAppUri(o: Occasion): String = "$OCCASION_URI_PREFIX${o.contactId}/${o.type.name}"

fun parseOccasionUri(uri: String?): Pair<Long, OccasionType>? {
    val match = uri?.let { OCCASION_URI_RE.matchEntire(it) } ?: return null
    val (contactId, typeName) = match.destructured
    // toLongOrNull: this now parses every rendered row, and a hostile 19+ digit
    // id must read as not-ours, not throw.
    val type = runCatching { OccasionType.valueOf(typeName) }.getOrNull()
    return contactId.toLongOrNull()?.let { id -> type?.let { id to it } }
}

// row-scoped ownership, the same predicate OccasionSync reconciles by: only a
// row stamped with a parseable occasion URI is app-generated. calendar
// membership is NOT ownership: the provisioned calendars accept hand-added
// rows, which must keep their own titles and notes.
fun isOwnedOccasionUri(uri: String?): Boolean = parseOccasionUri(uri) != null

fun occasionDtStartMillis(month: Int, day: Int, year: Int?): Long {
    // the parser accepts Feb 29 for any year and defers leap-validity here; a
    // non-leap real year can't build a LocalDate, so fall back to the leap sentinel
    // (the age just won't render, same as a no-year occasion) rather than throwing
    // and crashing occasion sync.
    val resolvedYear = year ?: OCCASION_NO_YEAR_SENTINEL
    val date = runCatching { LocalDate.of(resolvedYear, month, day) }
        .getOrElse { LocalDate.of(OCCASION_NO_YEAR_SENTINEL, month, day) }
    return date
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
}

fun occasionEventDraft(o: Occasion, calendarId: Long, appPackage: String, title: String, name: String): EventDraft {
    val start = occasionDtStartMillis(o.month, o.day, o.year)
    return EventDraft(
        calendarId = calendarId,
        title = title,
        description = name,
        location = null,
        startMillis = start,
        endMillis = start + TimeUnits.MillisPerDay,
        allDay = true,
        eventTimezone = "UTC",
        rrule = RecurrenceRule.build(RecurrenceFrequency.Yearly, allDay = true),
        customAppPackage = appPackage,
        customAppUri = occasionCustomAppUri(o),
    )
}
