/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.ui.eventedit

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class EventEditLocalRangeTest {
    private val hourMs = 3_600_000L
    private val dayMs = 86_400_000L

    // timed events extract in the given zone, so the clock fields reflect local time.
    @Test
    fun `timed event extracts in the supplied zone`() {
        val range = extractLocalRange(
            startMillis = 0L,
            endMillis = hourMs,
            allDay = false,
            rrule = null,
            instanceStartMillis = null,
            zone = ZoneOffset.ofHours(2),
        )
        assertEquals(LocalDate.of(1970, 1, 1), range.startDate)
        assertEquals(LocalTime.of(2, 0), range.startTime)
        assertEquals(LocalTime.of(3, 0), range.endTime)
    }

    // all-day rows are stored at UTC midnight: extract in UTC (not the local
    // zone) and drop the exclusive end day so a one-day event reads as one day.
    @Test
    fun `all-day event extracts in UTC and shows an inclusive end date`() {
        val range = extractLocalRange(
            startMillis = 0L,
            endMillis = dayMs,
            allDay = true,
            rrule = null,
            instanceStartMillis = null,
            zone = ZoneOffset.ofHours(-5),
        )
        assertEquals(LocalDate.of(1970, 1, 1), range.startDate)
        assertEquals(LocalDate.of(1970, 1, 1), range.endDate)
        assertEquals(LocalTime.MIDNIGHT, range.startTime)
    }

    // a malformed all-day row (exclusive end == start, e.g. a foreign null or
    // garbage DURATION reconstructed to zero length) must not open the editor
    // with the end a day before the start; clamp it to a single day.
    @Test
    fun `zero-length all-day row clamps the end date to the start`() {
        val range = extractLocalRange(
            startMillis = 0L,
            endMillis = 0L,
            allDay = true,
            rrule = null,
            instanceStartMillis = null,
            zone = ZoneOffset.UTC,
        )
        assertEquals(LocalDate.of(1970, 1, 1), range.startDate)
        assertEquals(LocalDate.of(1970, 1, 1), range.endDate)
    }

    // a recurring event opened from an instance prefills that instance's day,
    // preserving the parent's duration, not the parent DTSTART.
    @Test
    fun `recurring event opened from an instance uses the instance slot`() {
        val range = extractLocalRange(
            startMillis = 0L,
            endMillis = hourMs,
            allDay = false,
            rrule = "FREQ=DAILY",
            instanceStartMillis = dayMs,
            zone = ZoneOffset.UTC,
        )
        assertEquals(LocalDate.of(1970, 1, 2), range.startDate)
        assertEquals(LocalTime.MIDNIGHT, range.startTime)
        assertEquals(LocalTime.of(1, 0), range.endTime)
    }

    // a malformed zero-length timed row (end == start) would reseed the editor
    // with end == start, which the save guard then rejects (blocking even a
    // title-only edit); widen the display end to the default duration.
    @Test
    fun `zero-length timed row widens the end to the default duration`() {
        val range = extractLocalRange(
            startMillis = hourMs,
            endMillis = hourMs,
            allDay = false,
            rrule = null,
            instanceStartMillis = null,
            zone = ZoneOffset.UTC,
            defaultDurationMinutes = 30,
        )
        assertEquals(LocalTime.of(1, 0), range.startTime)
        assertEquals(LocalTime.of(1, 30), range.endTime)
        assertEquals(range.startDate, range.endDate)
    }

    // a legitimately short timed event (end > start, even under the default
    // duration) keeps its real end; only degenerate rows are widened.
    @Test
    fun `short timed event keeps its real end`() {
        val range = extractLocalRange(
            startMillis = 0L,
            endMillis = 15 * 60_000L,
            allDay = false,
            rrule = null,
            instanceStartMillis = null,
            zone = ZoneOffset.UTC,
            defaultDurationMinutes = 60,
        )
        assertEquals(LocalTime.of(0, 15), range.endTime)
    }
}
