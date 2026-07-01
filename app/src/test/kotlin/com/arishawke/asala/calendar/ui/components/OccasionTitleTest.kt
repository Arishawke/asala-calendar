/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.components

import com.arishawke.asala.calendar.data.OCCASION_NO_YEAR_SENTINEL
import com.arishawke.asala.calendar.data.OccasionKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private fun utcMillis(year: Int, month: Int = 1, day: Int = 1): Long =
    LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

class OccasionTitleTest {
    @Test fun `birthday with known year computes age`() {
        val result = OccasionTitle.compute(
            kind = OccasionKind.Birthday,
            name = "Alice",
            baseTitle = "Alice's birthday",
            parentDtStartMillis = utcMillis(1990),
            occurrenceStartMillis = utcMillis(2020),
        )
        assertEquals(OccasionTitleResult.BirthdayAge("Alice", 30), result)
    }

    @Test fun `anniversary with known year computes ordinal count`() {
        val result = OccasionTitle.compute(
            kind = OccasionKind.Anniversary,
            name = "Bob",
            baseTitle = "Bob's anniversary",
            parentDtStartMillis = utcMillis(2015),
            occurrenceStartMillis = utcMillis(2020),
        )
        assertEquals(OccasionTitleResult.AnniversaryOrdinal("Bob", 5), result)
    }

    @Test fun `sentinel birth year falls back to base title`() {
        val result = OccasionTitle.compute(
            kind = OccasionKind.Birthday,
            name = "Alice",
            baseTitle = "Alice's birthday",
            parentDtStartMillis = utcMillis(OCCASION_NO_YEAR_SENTINEL),
            occurrenceStartMillis = utcMillis(2020),
        )
        assertEquals(OccasionTitleResult.Base("Alice's birthday"), result)
    }

    @Test fun `kind None falls back to base title`() {
        val result = OccasionTitle.compute(
            kind = OccasionKind.None,
            name = "Alice",
            baseTitle = "Standup",
            parentDtStartMillis = utcMillis(1990),
            occurrenceStartMillis = utcMillis(2020),
        )
        assertEquals(OccasionTitleResult.Base("Standup"), result)
    }

    @Test fun `null name falls back to base title`() {
        val result = OccasionTitle.compute(
            kind = OccasionKind.Birthday,
            name = null,
            baseTitle = "Someone's birthday",
            parentDtStartMillis = utcMillis(1990),
            occurrenceStartMillis = utcMillis(2020),
        )
        assertEquals(OccasionTitleResult.Base("Someone's birthday"), result)
    }

    @Test fun `non-positive count falls back to base title`() {
        val sameYear = OccasionTitle.compute(
            kind = OccasionKind.Birthday,
            name = "Alice",
            baseTitle = "Alice's birthday",
            parentDtStartMillis = utcMillis(2020),
            occurrenceStartMillis = utcMillis(2020),
        )
        assertEquals(OccasionTitleResult.Base("Alice's birthday"), sameYear)

        val futureDated = OccasionTitle.compute(
            kind = OccasionKind.Birthday,
            name = "Alice",
            baseTitle = "Alice's birthday",
            parentDtStartMillis = utcMillis(2021),
            occurrenceStartMillis = utcMillis(2020),
        )
        assertEquals(OccasionTitleResult.Base("Alice's birthday"), futureDated)
    }
}
