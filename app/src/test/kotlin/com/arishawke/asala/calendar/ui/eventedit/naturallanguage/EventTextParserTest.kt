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

    // a bare "N-M" without a meridiem or from/to is not a time range; it is left
    // for the date parser / title (prevents "jan 3-15" from becoming 3am-3pm).
    @Test
    fun `bare number range is not a time`() {
        val p = parse("conference 3-15")
        assertNull(p.startTime)
        assertNull(p.endTime)
    }

    // meridiem on the start side carries to the end ("9am-10" is 9am-10am).
    @Test
    fun `range inherits meridiem from the start`() {
        val p = parse("shift 9am-10")
        assertEquals(LocalTime.of(9, 0), p.startTime)
        assertEquals(LocalTime.of(10, 0), p.endTime)
    }

    // "at <place>" is a location only when it is not a time. these two share the
    // word "at" but must split correctly.
    @Test fun `at a place is a location`() {
        val p = parse("lunch at the office")
        assertEquals("lunch", p.title)
        assertEquals("the office", p.location)
        assertNull(p.startTime)
    }

    @Test fun `at a time is not a location`() {
        val p = parse("lunch at 3pm")
        assertEquals(LocalTime.of(15, 0), p.startTime)
        assertNull(p.location)
        assertEquals("lunch", p.title)
    }

    // the canonical full phrase: time and date trail the location and are
    // claimed first, leaving a clean location and title.
    @Test fun `full phrase splits title location date and time`() {
        val p = parse("lunch at Cafe Rio tomorrow at noon")
        assertEquals("lunch", p.title)
        assertEquals("Cafe Rio", p.location)
        assertEquals(now.toLocalDate().plusDays(1), p.date)
        assertEquals(LocalTime.NOON, p.startTime)
    }

    // a connector left dangling by a blanked date is trimmed off the location.
    @Test fun `trailing connector is trimmed from location`() {
        val p = parse("dinner at Nonna's on friday")
        assertEquals("Nonna's", p.location)
    }
}
