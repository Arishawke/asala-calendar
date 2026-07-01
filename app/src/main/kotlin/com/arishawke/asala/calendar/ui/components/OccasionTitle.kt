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

// non-occasion events (occasion == None) resolve to item.title immediately,
// so callers can swap this in for the raw title unconditionally.
@Composable
internal fun occasionDisplayTitle(item: EventItem): String = occasionResultText(
    OccasionTitle.compute(
        kind = item.occasion,
        name = item.occasionName,
        baseTitle = item.title,
        parentDtStartMillis = item.parentDtStartMillis,
        occurrenceStartMillis = item.startMillis,
    ),
)

// EventDetail reads the parent Events row only (no per-instance occurrence
// date), so parent and occurrence years are the same value here; a
// recurring occasion series always falls back to the base title in the
// detail sheet until EventDetail carries the shown instance's start.
@Composable
internal fun occasionDisplayTitle(detail: EventDetail): String = occasionResultText(
    OccasionTitle.compute(
        kind = detail.occasion,
        name = detail.description,
        baseTitle = detail.title,
        parentDtStartMillis = detail.startMillis,
        occurrenceStartMillis = detail.startMillis,
    ),
)

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
