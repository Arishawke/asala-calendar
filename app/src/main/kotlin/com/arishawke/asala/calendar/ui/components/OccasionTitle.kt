/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.components

import android.icu.text.MessageFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.EventDetail
import com.arishawke.asala.calendar.data.EventItem
import com.arishawke.asala.calendar.data.OCCASION_NO_YEAR_SENTINEL
import com.arishawke.asala.calendar.data.OccasionKind
import com.arishawke.asala.calendar.ui.LocalOccasionCalendarIds
import com.arishawke.asala.calendar.ui.multidaybars.WeekSegment
import java.time.Instant
import java.time.ZoneOffset

// render-time title for occasion events: the stored row keeps the plain base
// title ("Alice's birthday"), so the age/ordinal is derived here from the
// parent DTSTART year vs. the shown occurrence's year and never needs a
// yearly rename write.
internal sealed interface OccasionTitleResult {
    data class Base(val title: String) : OccasionTitleResult
    data class BirthdayAge(val name: String, val age: Int) : OccasionTitleResult
    data class AnniversaryOrdinal(val name: String, val ordinal: Int) : OccasionTitleResult
}

internal object OccasionTitle {
    fun compute(
        kind: OccasionKind,
        name: String?,
        baseTitle: String,
        parentDtStartMillis: Long,
        occurrenceStartMillis: Long,
    ): OccasionTitleResult {
        val (validName, count) = validOccasion(kind, name, parentDtStartMillis, occurrenceStartMillis)
            ?: return OccasionTitleResult.Base(baseTitle)
        return resultFor(kind, validName, count, baseTitle)
    }

    // non-null only when an age/ordinal is showable: a known occasion kind, a
    // contact name, a real (non-sentinel) birth/founding year, and a positive
    // count (guards a same-year or future-dated contact).
    private fun validOccasion(
        kind: OccasionKind,
        name: String?,
        parentDtStartMillis: Long,
        occurrenceStartMillis: Long,
    ): Pair<String, Int>? {
        val birthYear = utcYear(parentDtStartMillis)
        if (kind == OccasionKind.None || name == null || birthYear == OCCASION_NO_YEAR_SENTINEL) return null
        val count = utcYear(occurrenceStartMillis) - birthYear
        return (name to count).takeIf { count > 0 }
    }

    private fun resultFor(kind: OccasionKind, name: String, count: Int, baseTitle: String): OccasionTitleResult =
        when (kind) {
            OccasionKind.Birthday -> OccasionTitleResult.BirthdayAge(name, count)
            OccasionKind.Anniversary -> OccasionTitleResult.AnniversaryOrdinal(name, count)
            // validOccasion already excludes None before count is computed.
            OccasionKind.None -> OccasionTitleResult.Base(baseTitle)
        }

    private fun utcYear(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().year
}

// the kind to TITLE by: a third-party calendar merely NAMED like an occasion
// classifies as one (so the cake icon still shows) but is not owned by the app,
// so its events title as None and keep their own text instead of a "<desc> turns
// N" relabel (audit F4). owned = the event is in a provisioned occasion calendar.
internal fun occasionTitlingKind(occasion: OccasionKind, owned: Boolean): OccasionKind =
    if (owned) occasion else OccasionKind.None

// non-occasion events (occasion == None) resolve to item.title immediately,
// so callers can swap this in for the raw title unconditionally.
@Composable
internal fun occasionDisplayTitle(item: EventItem): String = occasionResultText(
    OccasionTitle.compute(
        kind = occasionTitlingKind(item.occasion, item.calendarId in LocalOccasionCalendarIds.current),
        name = item.occasionName,
        baseTitle = item.title,
        parentDtStartMillis = item.parentDtStartMillis,
        occurrenceStartMillis = item.startMillis,
    ),
)

// detail.startMillis is the parent DTSTART; instanceMillis (when present) is
// the tapped occurrence's start, so a recurring occasion series still ages
// up per-occurrence instead of always falling back to the base title.
@Composable
internal fun occasionDisplayTitle(detail: EventDetail, instanceMillis: Long?): String = occasionResultText(
    OccasionTitle.compute(
        kind = occasionTitlingKind(detail.occasion, detail.calendarId in LocalOccasionCalendarIds.current),
        name = detail.description,
        baseTitle = detail.title,
        parentDtStartMillis = detail.startMillis,
        occurrenceStartMillis = instanceMillis ?: detail.startMillis,
    ),
)

// week/3-day all-day bars render WeekSegment, not EventItem; occurrenceStartMillis
// is the segment's own start (birthdays/anniversaries are always single-day).
@Composable
internal fun occasionDisplayTitle(segment: WeekSegment): String = occasionResultText(
    OccasionTitle.compute(
        kind = occasionTitlingKind(segment.occasion, segment.calendarId in LocalOccasionCalendarIds.current),
        name = segment.occasionName,
        baseTitle = segment.title,
        parentDtStartMillis = segment.parentDtStartMillis,
        occurrenceStartMillis = segment.occurrenceStartMillis,
    ),
)

// shared result -> string mapping so the item/detail/segment overloads can't drift.
@Composable
private fun occasionResultText(result: OccasionTitleResult): String = when (result) {
    is OccasionTitleResult.Base -> result.title
    is OccasionTitleResult.BirthdayAge -> stringResource(R.string.occasion_birthday_age, result.name, result.age)
    is OccasionTitleResult.AnniversaryOrdinal ->
        stringResource(R.string.occasion_anniversary_ordinal, result.name, ordinalString(result.ordinal))
}

@Composable
private fun ordinalString(n: Int): String {
    val locale = LocalConfiguration.current.locales.get(0)
    return MessageFormat("{0,ordinal}", locale).format(arrayOf(n))
}
