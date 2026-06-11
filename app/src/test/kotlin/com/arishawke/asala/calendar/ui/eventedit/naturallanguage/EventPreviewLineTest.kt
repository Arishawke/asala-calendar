/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.ui.eventedit.naturallanguage

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class EventPreviewLineTest {
    // nothing structured recognized -> no preview (the bare title is shown in the
    // Title field, not echoed here).
    @Test fun `title only yields no preview`() {
        assertNull(previewLine(ParsedEvent(title = "call mom"), is24Hour = false, locale = Locale.US))
    }

    // a timed parse shows date and a time range.
    @Test fun `timed preview shows date and time`() {
        val line = previewLine(
            ParsedEvent(
                title = "lunch",
                date = LocalDate.of(2026, 6, 11),
                startTime = LocalTime.NOON,
                endTime = LocalTime.of(13, 0),
            ),
            is24Hour = true,
            locale = Locale.US,
        )!!
        assertTrue(line.contains("12:00"))
        assertTrue(line.contains("13:00"))
        assertTrue(line.contains("Jun"))
    }

    // location is appended when present.
    @Test fun `location appears in preview`() {
        val line = previewLine(
            ParsedEvent(title = "lunch", date = LocalDate.of(2026, 6, 11), location = "Cafe Rio"),
            is24Hour = false,
            locale = Locale.US,
        )!!
        assertTrue(line.contains("Cafe Rio"))
    }
}
