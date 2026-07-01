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

private const val MILLIS_PER_DAY = 86_400_000L

private val OCCASION_URI_RE = Regex("^asala://occasion/(\\d+)/(\\w+)$")

// identity for app-owned occasion events (ADR-0002 rule 7: no sync-adapter
// writes, so CUSTOM_APP_PACKAGE/CUSTOM_APP_URI stand in for _SYNC_ID).
fun occasionCustomAppUri(o: Occasion): String = "asala://occasion/${o.contactId}/${o.type.name}"

fun parseOccasionUri(uri: String?): Pair<Long, OccasionType>? {
    val match = uri?.let { OCCASION_URI_RE.matchEntire(it) } ?: return null
    val (contactId, typeName) = match.destructured
    return runCatching { OccasionType.valueOf(typeName) }.getOrNull()?.let { contactId.toLong() to it }
}

fun occasionDtStartMillis(month: Int, day: Int, year: Int?): Long =
    LocalDate.of(year ?: OCCASION_NO_YEAR_SENTINEL, month, day)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

fun occasionEventDraft(o: Occasion, calendarId: Long, appPackage: String, title: String, name: String): EventDraft {
    val start = occasionDtStartMillis(o.month, o.day, o.year)
    return EventDraft(
        calendarId = calendarId,
        title = title,
        description = name,
        location = null,
        startMillis = start,
        endMillis = start + MILLIS_PER_DAY,
        allDay = true,
        eventTimezone = "UTC",
        rrule = RecurrenceRule.build(RecurrenceFrequency.Yearly, allDay = true),
        customAppPackage = appPackage,
        customAppUri = occasionCustomAppUri(o),
    )
}
