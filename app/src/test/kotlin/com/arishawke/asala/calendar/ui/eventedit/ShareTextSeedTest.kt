/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.ui.eventedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class ShareTextSeedTest {
    private val now = LocalDateTime.of(2026, 7, 2, 9, 0) // Thu 2026-07-02
    private val seededDate = LocalDate.of(2026, 7, 2)
    private fun form() = EventEditFormState(
        startDate = seededDate,
        startTime = LocalTime.of(10, 0),
        endDate = seededDate,
        endTime = LocalTime.of(11, 0),
        defaultDurationMinutes = 60,
    )

    // share text runs through the same parser + withParsed overlay as the
    // quick-add Apply button, applied once at construction.
    @Test fun `share text present applies the parse to the seeded form`() {
        val s = seedFormWithShareText(
            base = form(),
            shareText = "dinner with Sam tomorrow at 7pm",
            now = now,
            locale = Locale.US,
        )
        assertFalse(s.allDay)
        assertEquals("dinner with Sam", s.title)
        assertEquals(now.toLocalDate().plusDays(1), s.startDate)
        assertEquals(LocalTime.of(19, 0), s.startTime)
    }

    // no share text is a no-op: the seeded form comes back unchanged (same
    // instance), so a plain FAB-opened create editor is unaffected.
    @Test fun `null share text leaves the form untouched`() {
        val base = form()
        assertSame(base, seedFormWithShareText(base, shareText = null, now = now, locale = Locale.US))
    }

    // blank share text (defensive: the normalizer already drops this
    // upstream) is also a no-op rather than parsing an empty string.
    @Test fun `blank share text leaves the form untouched`() {
        val base = form()
        assertSame(base, seedFormWithShareText(base, shareText = "   ", now = now, locale = Locale.US))
    }
}
