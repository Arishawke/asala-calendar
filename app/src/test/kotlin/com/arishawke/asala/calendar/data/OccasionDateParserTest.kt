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
}
