/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.ui.eventedit.naturallanguage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DateGrammarTest {
    private val today = LocalDate.of(2026, 6, 10) // Wednesday
    private fun find(text: String) = DateGrammar.find(text, today, Locale.US)?.date

    @Test fun `today and tonight resolve to today`() {
        assertEquals(today, find("ship today"))
        assertEquals(today, find("call tonight"))
    }

    @Test fun `tomorrow is plus one day`() {
        assertEquals(today.plusDays(1), find("dentist tomorrow"))
    }

    @Test fun `in N days and weeks`() {
        assertEquals(today.plusDays(3), find("trip in 3 days"))
        assertEquals(today.plusWeeks(2), find("review in 2 weeks"))
    }

    // a bare weekday is today when it matches, else the next future occurrence,
    // because you cannot schedule into the past.
    @Test fun `bare weekday rolls forward, same-day stays`() {
        assertEquals(LocalDate.of(2026, 6, 12), find("standup friday")) // Wed -> Fri
        assertEquals(today, find("standup wednesday")) // today
        assertEquals(LocalDate.of(2026, 6, 15), find("standup monday")) // next Monday
    }

    // "next X" is one week beyond the upcoming X, a deliberate, documented rule.
    @Test fun `next weekday is a week beyond the upcoming one`() {
        assertEquals(LocalDate.of(2026, 6, 19), find("retro next friday")) // upcoming Fri 12 + 7
        assertEquals(LocalDate.of(2026, 6, 17), find("retro next wednesday")) // today + 7
    }

    // "this X" is the upcoming occurrence (same as bare), distinct from "next X".
    @Test fun `this weekday is the upcoming occurrence`() {
        assertEquals(LocalDate.of(2026, 6, 12), find("retro this friday"))
        assertEquals(today, find("retro this wednesday"))
    }

    @Test fun `no date returns null`() {
        assertNull(find("call mom"))
    }
}
