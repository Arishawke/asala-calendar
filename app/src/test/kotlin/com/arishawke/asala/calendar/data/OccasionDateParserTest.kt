/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OccasionDateParserTest {
    @Test fun `parses full date with year`() {
        assertEquals(ParsedOccasionDate(6, 15, 1990), OccasionDateParser.parse("1990-06-15"))
    }

    @Test fun `parses no-year form`() {
        assertEquals(ParsedOccasionDate(12, 25, null), OccasionDateParser.parse("--12-25"))
    }

    @Test fun `parses leap day`() {
        assertEquals(ParsedOccasionDate(2, 29, 2000), OccasionDateParser.parse("2000-02-29"))
    }

    @Test fun `rejects garbage`() {
        assertNull(OccasionDateParser.parse("not a date"))
        assertNull(OccasionDateParser.parse(""))
        assertNull(OccasionDateParser.parse(null))
        assertNull(OccasionDateParser.parse("1990-13-40"))
    }

    // day-ceiling boundaries: the only prior invalid case (1990-13-40) short-circuits
    // on month > 12, leaving maxDayOf untested. these pin per-month day limits.
    @Test fun `rejects impossible days in an otherwise valid month`() {
        assertNull(OccasionDateParser.parse("1990-02-30"))
        assertNull(OccasionDateParser.parse("1990-04-31"))
        assertNull(OccasionDateParser.parse("1990-00-15"))
        assertNull(OccasionDateParser.parse("1990-06-00"))
    }

    @Test fun `accepts the last day of a 30-day month`() {
        assertEquals(ParsedOccasionDate(4, 30, 1990), OccasionDateParser.parse("1990-04-30"))
    }

    // pins LONG_MONTH_MAX_DAY = 31: a regression to 30 would silently drop
    // every Dec-31 birthday.
    @Test fun `accepts the last day of a 31-day month`() {
        assertEquals(ParsedOccasionDate(12, 31, 1990), OccasionDateParser.parse("1990-12-31"))
    }

    // Feb 29 is accepted regardless of year here; leap-validity is deferred to
    // occasionDtStartMillis (see OccasionEventTest). documents the contract F2 relies on.
    @Test fun `accepts Feb 29 with a non-leap year (deferred to dtstart)`() {
        assertEquals(ParsedOccasionDate(2, 29, 2001), OccasionDateParser.parse("2001-02-29"))
    }
}
