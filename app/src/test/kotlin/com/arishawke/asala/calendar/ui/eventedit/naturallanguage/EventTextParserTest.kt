/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.ui.eventedit.naturallanguage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class EventTextParserTest {
    private val now = LocalDateTime.of(2026, 6, 10, 9, 0) // Wed 2026-06-10
    private fun parse(text: String) = EventTextParser.parse(text, now, Locale.US)

    // no recognizable date/time/location: the whole phrase is the title and
    // nothing else is invented, so a plain note still becomes a usable event.
    @Test
    fun `plain phrase becomes the title with no fields`() {
        val p = parse("call mom")
        assertEquals("call mom", p.title)
        assertNull(p.date)
        assertNull(p.startTime)
        assertNull(p.endTime)
        assertNull(p.location)
    }

    // empty input must not throw and yields an empty title (the editor's save
    // already substitutes "(No title)").
    @Test
    fun `blank input yields empty title and no throw`() {
        assertEquals("", parse("   ").title)
    }

    // noon/midnight are named times; "at" is part of the time phrase, not a
    // location cue, so it must not leak into the title.
    @Test
    fun `at noon is a timed event at twelve`() {
        val p = parse("lunch at noon")
        assertEquals("lunch", p.title)
        assertEquals(LocalTime.NOON, p.startTime)
        assertNull(p.location)
    }

    @Test
    fun `midnight maps to start of day`() {
        assertEquals(LocalTime.MIDNIGHT, parse("party midnight").startTime)
    }

    // 12-hour clock with am/pm, with and without minutes.
    @Test
    fun `pm clock parses with minutes`() {
        val p = parse("standup 3:30pm")
        assertEquals(LocalTime.of(15, 30), p.startTime)
        assertEquals("standup", p.title)
    }

    @Test
    fun `am twelve is midnight and pm twelve is noon`() {
        assertEquals(LocalTime.of(0, 0), parse("x 12am").startTime)
        assertEquals(LocalTime.of(12, 0), parse("x 12pm").startTime)
    }

    // 24-hour clock.
    @Test
    fun `twenty-four hour clock parses`() {
        assertEquals(LocalTime.of(15, 30), parse("sync 15:30").startTime)
    }

    // a bare hour is a time only when introduced by "at" (so "team 3" is not
    // 3am). "at 3" -> 03:00.
    @Test
    fun `bare hour needs at`() {
        assertEquals(LocalTime.of(3, 0), parse("call at 3").startTime)
    }

    // a range sets both ends; meridiem on the end side applies to the start
    // ("2-3pm" is 2pm-3pm, not 2am-3pm).
    @Test
    fun `range inherits meridiem from the end`() {
        val p = parse("meeting 2-3pm")
        assertEquals(LocalTime.of(14, 0), p.startTime)
        assertEquals(LocalTime.of(15, 0), p.endTime)
    }

    @Test
    fun `from-to range with minutes`() {
        val p = parse("standup from 9 to 9:15am")
        assertEquals(LocalTime.of(9, 0), p.startTime)
        assertEquals(LocalTime.of(9, 15), p.endTime)
    }

    // a duration with a start time yields an end time; the duration words are
    // not left in the title.
    @Test
    fun `duration adds an end time`() {
        val p = parse("focus at 9am for 90 minutes")
        assertEquals(LocalTime.of(9, 0), p.startTime)
        assertEquals(LocalTime.of(10, 30), p.endTime)
        assertEquals("focus", p.title)
    }

    @Test
    fun `fractional hour duration`() {
        val p = parse("deep work at 9am for 1.5 hours")
        assertEquals(LocalTime.of(10, 30), p.endTime)
    }
}
