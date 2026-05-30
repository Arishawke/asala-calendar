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
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlapLayoutTest {
    private fun ev(title: String, startMillis: Long, endMillis: Long): DayClippedEvent {
        val item = EventItem(
            instanceId = 0L,
            eventId = 0L,
            calendarId = 0L,
            title = title,
            startMillis = startMillis,
            endMillis = endMillis,
            allDay = false,
            displayColor = 0,
        )
        return DayClippedEvent(
            event = item,
            displayStartMillis = startMillis,
            displayEndMillis = endMillis,
            continuedFromPrev = false,
            continuedToNext = false,
            segmentIndex = 1,
            segmentCount = 1,
        )
    }

    @Test fun empty_input_returns_empty_list() {
        assertEquals(emptyList<LaidOutEvent>(), layOutOverlaps(emptyList()))
    }

    @Test fun single_event_gets_column_zero_and_width_one() {
        val result = layOutOverlaps(listOf(ev("A", 0L, 3600_000L)))
        assertEquals(1, result.size)
        assertEquals(0, result[0].columnIndex)
        assertEquals(1, result[0].clusterWidth)
    }

    @Test fun two_non_overlapping_events_are_separate_singletons() {
        // A ends before B starts: no overlap
        val a = ev("A", 0L, 3600_000L)
        val b = ev("B", 3600_000L, 7200_000L)
        val result = layOutOverlaps(listOf(b, a)) // intentionally reversed input

        assertEquals(2, result.size)
        val byTitle = result.associateBy { it.clipped.event.title }
        assertEquals(0, byTitle["A"]!!.columnIndex)
        assertEquals(1, byTitle["A"]!!.clusterWidth)
        assertEquals(0, byTitle["B"]!!.columnIndex)
        assertEquals(1, byTitle["B"]!!.clusterWidth)
    }

    @Test fun two_overlapping_events_split_into_two_columns() {
        val a = ev("A", 0L, 7200_000L)
        val b = ev("B", 3600_000L, 10800_000L)
        val result = layOutOverlaps(listOf(b, a)) // reversed input

        assertEquals(2, result.size)
        val byTitle = result.associateBy { it.clipped.event.title }
        assertEquals(0, byTitle["A"]!!.columnIndex)
        assertEquals(2, byTitle["A"]!!.clusterWidth)
        assertEquals(1, byTitle["B"]!!.columnIndex)
        assertEquals(2, byTitle["B"]!!.clusterWidth)
    }

    @Test fun three_chain_overlapping_events_share_one_cluster() {
        // A: 0-4h, B: 2-6h, C: 5-9h
        // A overlaps B, B overlaps C, A does NOT overlap C (A ends at 4h, C starts at 5h)
        // All in one cluster because they are transitively connected.
        // Width = 2 because at most 2 overlap at once; C reuses column 0.
        val a = ev("A", 0L, 14_400_000L) // 0-4h
        val b = ev("B", 7_200_000L, 21_600_000L) // 2-6h
        val c = ev("C", 18_000_000L, 32_400_000L) // 5-9h

        val result = layOutOverlaps(listOf(c, b, a)) // reversed input

        assertEquals(3, result.size)
        val byTitle = result.associateBy { it.clipped.event.title }

        assertEquals(0, byTitle["A"]!!.columnIndex)
        assertEquals(2, byTitle["A"]!!.clusterWidth)

        assertEquals(1, byTitle["B"]!!.columnIndex)
        assertEquals(2, byTitle["B"]!!.clusterWidth)

        // C starts after A ends; first-fit puts it back in column 0
        assertEquals(0, byTitle["C"]!!.columnIndex)
        assertEquals(2, byTitle["C"]!!.clusterWidth)
    }

    @Test fun start_tie_broken_alphabetically() {
        val b = ev("B", 0L, 3600_000L)
        val a = ev("A", 0L, 3600_000L)
        val result = layOutOverlaps(listOf(b, a))

        assertEquals(2, result.size)
        // After sorting by (displayStartMillis, title): A comes first
        assertEquals("A", result[0].clipped.event.title)
        assertEquals(0, result[0].columnIndex)
        assertEquals("B", result[1].clipped.event.title)
        assertEquals(1, result[1].columnIndex)
        assertEquals(2, result[0].clusterWidth)
        assertEquals(2, result[1].clusterWidth)
    }

    @Test fun six_overlapping_events_match_screenshot_scenario() {
        // Six timed events on 2026-05-22 in UTC millis:
        // E1: 14:00-15:30  E2: 14:00-16:00  E3: 15:00-16:30
        // E4: 15:00-17:00  E5: 16:00-17:30  E6: 17:00-18:00
        // All transitively connected -> one cluster
        val base = 1_748_000_000_000L // arbitrary anchor in UTC, relative offsets suffice
        val h = 3_600_000L // one hour in ms

        val e1 = ev("E1", base + 0 * h, base + 1 * h + h / 2) // 14:00-15:30
        val e2 = ev("E2", base + 0 * h, base + 2 * h) // 14:00-16:00
        val e3 = ev("E3", base + 1 * h, base + 2 * h + h / 2) // 15:00-16:30
        val e4 = ev("E4", base + 1 * h, base + 3 * h) // 15:00-17:00
        val e5 = ev("E5", base + 2 * h, base + 3 * h + h / 2) // 16:00-17:30
        val e6 = ev("E6", base + 3 * h, base + 4 * h) // 17:00-18:00

        val result = layOutOverlaps(listOf(e3, e6, e1, e4, e2, e5)) // shuffled input

        assertEquals(6, result.size)

        // All events must be in one cluster; clusterWidth must be consistent per event
        val widths = result.map { it.clusterWidth }.toSet()
        assertEquals("all events share one cluster width", 1, widths.size)

        val clusterWidth = widths.single()
        assertTrue("cluster width should be >= 2", clusterWidth >= 2)

        // No event may have a columnIndex outside [0, clusterWidth)
        result.forEach { laid ->
            assertTrue(
                "columnIndex ${laid.columnIndex} out of range for ${laid.clipped.event.title}",
                laid.columnIndex in 0 until laid.clusterWidth,
            )
        }
    }

    @Test
    fun `separate clusters get distinct cluster indices`() {
        val rows = layOutOverlaps(
            listOf(
                ev("A", 0L, 60L * 60_000), // 0:00-1:00
                ev("B", 60L * 60_000, 120L * 60_000), // 1:00-2:00 (no overlap)
            ),
        )
        val byTitle = rows.associateBy { it.clipped.event.title }
        assertEquals(0, byTitle.getValue("A").clusterIndex)
        assertEquals(1, byTitle.getValue("B").clusterIndex)
    }

    @Test
    fun `overlapping events share one cluster index`() {
        val rows = layOutOverlaps(
            listOf(
                ev("A", 0L, 120L * 60_000), // 0:00-2:00
                ev("B", 60L * 60_000, 180L * 60_000), // 1:00-3:00 (overlaps A)
            ),
        )
        val byTitle = rows.associateBy { it.clipped.event.title }
        assertEquals(byTitle.getValue("A").clusterIndex, byTitle.getValue("B").clusterIndex)
    }

    @Test fun cluster_uses_display_millis_not_raw_event_millis() {
        // A timed event from prev day 23:30 to today 00:30 is clipped to
        // [00:00, 00:30) on today. A normal 09:00-10:00 event today does
        // NOT overlap with the clipped slice, so they should land in
        // separate clusters even though their RAW event millis would
        // suggest different ordering.
        val dayStart = 1_748_000_000_000L
        val crosserEvent = EventItem(
            instanceId = 1L,
            eventId = 1L,
            calendarId = 1L,
            title = "Crosser",
            startMillis = dayStart - 30 * 60_000L, // prev day 23:30
            endMillis = dayStart + 30 * 60_000L, // today 00:30
            allDay = false,
            displayColor = 0,
        )
        val crosser = DayClippedEvent(
            event = crosserEvent,
            displayStartMillis = dayStart, // today 00:00
            displayEndMillis = dayStart + 30 * 60_000L, // today 00:30
            continuedFromPrev = true,
            continuedToNext = false,
            segmentIndex = 2,
            segmentCount = 2,
        )
        val morning = ev("Morning", dayStart + 9 * 3_600_000L, dayStart + 10 * 3_600_000L)

        val result = layOutOverlaps(listOf(crosser, morning))
        assertEquals(2, result.size)
        // Both should be width 1 - separate clusters, no overlap.
        result.forEach { laid -> assertEquals(1, laid.clusterWidth) }
    }
}
