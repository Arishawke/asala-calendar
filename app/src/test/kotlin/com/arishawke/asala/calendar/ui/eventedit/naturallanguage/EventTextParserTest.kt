/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.ui.eventedit.naturallanguage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
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
}
