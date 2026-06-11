/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.ui.eventedit

import com.arishawke.asala.calendar.ui.eventedit.naturallanguage.ParsedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WithParsedTest {
    private val seededDate = LocalDate.of(2026, 6, 10)
    private fun form() = EventEditFormState(
        startDate = seededDate,
        startTime = LocalTime.of(10, 0),
        endDate = seededDate,
        endTime = LocalTime.of(11, 0),
        defaultDurationMinutes = 60,
    )

    // a timed parse sets a timed event and derives the end from the default
    // duration when none was given.
    @Test fun `timed parse with no end uses default duration`() {
        val s = form().withParsed(
            ParsedEvent(title = "lunch", startTime = LocalTime.NOON, date = LocalDate.of(2026, 6, 11)),
        )
        assertFalse(s.allDay)
        assertEquals("lunch", s.title)
        assertEquals(LocalDate.of(2026, 6, 11), s.startDate)
        assertEquals(LocalTime.NOON, s.startTime)
        assertEquals(LocalTime.of(13, 0), s.endTime)
    }

    // a date with no time is an all-day event, because "dentist tuesday" should
    // block the day, not invent a phantom hour.
    @Test fun `date with no time becomes all-day`() {
        val s = form().withParsed(ParsedEvent(title = "dentist", date = LocalDate.of(2026, 6, 12)))
        assertTrue(s.allDay)
        assertEquals(LocalDate.of(2026, 6, 12), s.startDate)
        assertEquals(LocalDate.of(2026, 6, 12), s.endDate)
    }

    // a parse that recognized no date keeps the editor's seeded viewed date,
    // never silently forcing today.
    @Test fun `missing date keeps the seeded date`() {
        val s = form().withParsed(ParsedEvent(title = "call", startTime = LocalTime.of(9, 0)))
        assertEquals(seededDate, s.startDate)
    }

    // an explicit range is preserved exactly.
    @Test fun `range end is preserved`() {
        val s = form().withParsed(
            ParsedEvent(title = "sync", startTime = LocalTime.of(14, 0), endTime = LocalTime.of(15, 0)),
        )
        assertEquals(LocalTime.of(15, 0), s.endTime)
    }

    // a blank parsed title does not wipe a title the user already typed.
    @Test fun `blank parsed title does not overwrite`() {
        val s = form().copy(title = "kept").withParsed(ParsedEvent(title = "", date = seededDate))
        assertEquals("kept", s.title)
    }

    // unrecognized everything leaves the seeded form intact (title aside).
    @Test fun `no fields leaves the form untouched`() {
        val base = form()
        val s = base.withParsed(ParsedEvent(title = "note"))
        assertEquals(base.startTime, s.startTime)
        assertEquals(base.endTime, s.endTime)
        assertFalse(s.allDay)
    }
}
