/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

import com.arishawke.asala.calendar.data.EventItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DayClippedEventTest {
    private val zone = ZoneId.of("America/Los_Angeles")

    private fun atLocal(date: LocalDate, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant().toEpochMilli()

    private fun timed(start: Long, end: Long): EventItem = EventItem(
        instanceId = 1L,
        eventId = 1L,
        calendarId = 1L,
        title = "t",
        startMillis = start,
        endMillis = end,
        allDay = false,
        displayColor = 0,
    )

    @Test fun same_day_event_returns_unclipped() {
        val day = LocalDate.of(2026, 6, 1)
        val ev = timed(atLocal(day, 9), atLocal(day, 10))
        val clip = clipToDay(ev, day, zone)!!

        assertEquals(ev.startMillis, clip.displayStartMillis)
        assertEquals(ev.endMillis, clip.displayEndMillis)
        assertFalse(clip.continuedFromPrev)
        assertFalse(clip.continuedToNext)
    }

    @Test fun midnight_crosser_clips_to_each_day() {
        val tue = LocalDate.of(2026, 6, 2)
        val wed = LocalDate.of(2026, 6, 3)
        val ev = timed(atLocal(tue, 23, 30), atLocal(wed, 0, 30))

        val tueClip = clipToDay(ev, tue, zone)!!
        assertEquals(ev.startMillis, tueClip.displayStartMillis)
        assertEquals(atLocal(wed, 0, 0), tueClip.displayEndMillis)
        assertFalse(tueClip.continuedFromPrev)
        assertTrue(tueClip.continuedToNext)

        val wedClip = clipToDay(ev, wed, zone)!!
        assertEquals(atLocal(wed, 0, 0), wedClip.displayStartMillis)
        assertEquals(ev.endMillis, wedClip.displayEndMillis)
        assertTrue(wedClip.continuedFromPrev)
        assertFalse(wedClip.continuedToNext)
    }

    @Test fun event_outside_day_returns_null() {
        val day = LocalDate.of(2026, 6, 1)
        val other = LocalDate.of(2026, 6, 5)
        val ev = timed(atLocal(other, 9), atLocal(other, 10))
        assertNull(clipToDay(ev, day, zone))
    }

    @Test fun event_that_ends_exactly_at_day_start_does_not_clip_in() {
        val day = LocalDate.of(2026, 6, 2)
        val prev = LocalDate.of(2026, 6, 1)
        val ev = timed(atLocal(prev, 22), atLocal(day, 0)) // ends at 00:00 day-of
        assertNull(clipToDay(ev, day, zone))
    }

    @Test fun event_that_starts_exactly_at_next_day_start_does_not_clip_in() {
        val day = LocalDate.of(2026, 6, 2)
        val next = LocalDate.of(2026, 6, 3)
        val ev = timed(atLocal(next, 0), atLocal(next, 1)) // starts at 00:00 next-day
        assertNull(clipToDay(ev, day, zone))
    }

    @Test fun three_day_event_clips_middle_day_to_full_day() {
        val tue = LocalDate.of(2026, 6, 2)
        val wed = LocalDate.of(2026, 6, 3)
        val thu = LocalDate.of(2026, 6, 4)
        val ev = timed(atLocal(tue, 10), atLocal(thu, 20))

        val wedClip = clipToDay(ev, wed, zone)!!
        assertEquals(atLocal(wed, 0, 0), wedClip.displayStartMillis)
        assertEquals(atLocal(thu, 0, 0), wedClip.displayEndMillis)
        assertTrue(wedClip.continuedFromPrev)
        assertTrue(wedClip.continuedToNext)
    }

    @Test fun all_day_event_returns_null() {
        val day = LocalDate.of(2026, 6, 1)
        val allDay = EventItem(
            instanceId = 1L,
            eventId = 1L,
            calendarId = 1L,
            title = "all",
            startMillis = atLocal(day, 0),
            endMillis = atLocal(day.plusDays(1), 0),
            allDay = true,
            displayColor = 0,
        )
        // All-day events go through WeekBucketer instead; clipToDay
        // skips them so callers don't accidentally double-render.
        assertNull(clipToDay(allDay, day, zone))
    }

    @Test fun same_day_event_is_single_segment() {
        val day = LocalDate.of(2026, 6, 1)
        val ev = timed(atLocal(day, 9), atLocal(day, 10))
        val clip = clipToDay(ev, day, zone)!!
        assertEquals(1, clip.segmentIndex)
        assertEquals(1, clip.segmentCount)
    }

    @Test fun midnight_crosser_numbers_two_segments() {
        val tue = LocalDate.of(2026, 6, 2)
        val wed = LocalDate.of(2026, 6, 3)
        val ev = timed(atLocal(tue, 23, 30), atLocal(wed, 0, 30))

        val tueClip = clipToDay(ev, tue, zone)!!
        assertEquals(1, tueClip.segmentIndex)
        assertEquals(2, tueClip.segmentCount)

        val wedClip = clipToDay(ev, wed, zone)!!
        assertEquals(2, wedClip.segmentIndex)
        assertEquals(2, wedClip.segmentCount)
    }

    @Test fun three_day_event_numbers_three_segments() {
        val tue = LocalDate.of(2026, 6, 2)
        val wed = LocalDate.of(2026, 6, 3)
        val thu = LocalDate.of(2026, 6, 4)
        val ev = timed(atLocal(tue, 10), atLocal(thu, 20))

        assertEquals(1, clipToDay(ev, tue, zone)!!.segmentIndex)
        assertEquals(2, clipToDay(ev, wed, zone)!!.segmentIndex)
        assertEquals(3, clipToDay(ev, thu, zone)!!.segmentIndex)
        assertEquals(3, clipToDay(ev, tue, zone)!!.segmentCount)
    }

    // 10pm to midnight exactly does not cross into the next day, so it is one
    // segment, no continuation badge.
    @Test fun event_ending_exactly_at_midnight_is_single_segment() {
        val prev = LocalDate.of(2026, 6, 1)
        val day = LocalDate.of(2026, 6, 2)
        val ev = timed(atLocal(prev, 22), atLocal(day, 0))
        val clip = clipToDay(ev, prev, zone)!!
        assertEquals(1, clip.segmentIndex)
        assertEquals(1, clip.segmentCount)
    }

    @Test fun anchor_is_null_for_single_day_event() {
        val day = LocalDate.of(2026, 6, 1)
        val ev = timed(atLocal(day, 9), atLocal(day, 10))
        assertNull(segmentAnchorMillis(clipToDay(ev, day, zone)!!))
    }

    // First piece anchors to the real start, last piece to the real end, so a
    // crosser reads "start" on day 1 and "end" on day 2 (never a bare 00:00).
    @Test fun anchor_is_start_on_first_piece_and_end_on_last() {
        val tue = LocalDate.of(2026, 6, 2)
        val wed = LocalDate.of(2026, 6, 3)
        val ev = timed(atLocal(tue, 23, 30), atLocal(wed, 0, 30))
        assertEquals(ev.startMillis, segmentAnchorMillis(clipToDay(ev, tue, zone)!!))
        assertEquals(ev.endMillis, segmentAnchorMillis(clipToDay(ev, wed, zone)!!))
    }

    // A middle day fills the whole column; it shows only the badge, no time.
    @Test fun anchor_is_null_on_middle_piece_of_three_day_event() {
        val tue = LocalDate.of(2026, 6, 2)
        val wed = LocalDate.of(2026, 6, 3)
        val thu = LocalDate.of(2026, 6, 4)
        val ev = timed(atLocal(tue, 10), atLocal(thu, 20))
        assertNull(segmentAnchorMillis(clipToDay(ev, wed, zone)!!))
    }
}
