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
import org.junit.Test

class CrowdedOverflowTest {
    private fun ev(title: String, startMin: Long, endMin: Long): DayClippedEvent {
        val start = startMin * 60_000
        val end = endMin * 60_000
        return DayClippedEvent(
            event = EventItem(
                instanceId = startMin,
                eventId = startMin,
                calendarId = 1L,
                title = title,
                startMillis = start,
                endMillis = end,
                allDay = false,
                displayColor = 0,
            ),
            displayStartMillis = start,
            displayEndMillis = end,
            continuedFromPrev = false,
            continuedToNext = false,
            segmentIndex = 1,
            segmentCount = 1,
        )
    }

    @Test fun `empty input yields empty layout`() {
        val out = crowdedLayout(emptyList())
        assertEquals(0, out.visible.size)
        assertEquals(0, out.overflow.size)
    }

    @Test fun `single event is visible with no overflow`() {
        val out = crowdedLayout(listOf(ev("A", 0, 60)))
        assertEquals(1, out.visible.size)
        assertEquals(0, out.overflow.size)
    }

    @Test fun `two overlapping events stay visible with no overflow`() {
        val out = crowdedLayout(listOf(ev("A", 0, 120), ev("B", 60, 180)))
        assertEquals(2, out.visible.size)
        assertEquals(0, out.overflow.size)
    }

    @Test fun `three concurrent events collapse to column zero plus one overflow group`() {
        val out = crowdedLayout(listOf(ev("A", 0, 120), ev("B", 10, 120), ev("C", 20, 120)))
        assertEquals(1, out.visible.size)
        assertEquals(0, out.visible.first().columnIndex)
        assertEquals(1, out.overflow.size)
        assertEquals(2, out.overflow.first().collapsedCount)
        assertEquals(3, out.overflow.first().events.size)
        // includes the visible column-0 event, not just the collapsed ones
        assertEquals(
            setOf("A", "B", "C"),
            out.overflow.first().events.map { it.title }.toSet(),
        )
        // Chip anchors at the cluster's earliest start (event A at 0).
        assertEquals(0L, out.overflow.first().clusterStartMillis)
    }

    @Test fun `long chain with max concurrency two does not collapse`() {
        val out = crowdedLayout(
            listOf(ev("A", 0, 60), ev("B", 30, 90), ev("C", 60, 120), ev("D", 90, 150)),
        )
        assertEquals(4, out.visible.size)
        assertEquals(0, out.overflow.size)
    }

    @Test fun `crowded cluster and a separate event coexist`() {
        val out = crowdedLayout(
            listOf(
                ev("A", 0, 120),
                ev("B", 10, 120),
                ev("C", 20, 120),
                ev("Z", 600, 660),
            ),
        )
        assertEquals(2, out.visible.size)
        assertEquals(1, out.overflow.size)
        assertEquals(2, out.overflow.first().collapsedCount)
    }

    @Test fun `infinite threshold never collapses (day-view behavior)`() {
        val out = crowdedLayout(
            listOf(ev("A", 0, 120), ev("B", 10, 120), ev("C", 20, 120)),
            threshold = Int.MAX_VALUE,
        )
        assertEquals(3, out.visible.size)
        assertEquals(0, out.overflow.size)
    }

    @Test fun `two separate crowded clusters yield two overflow groups in time order`() {
        val out = crowdedLayout(
            listOf(
                ev("A", 0, 120),
                ev("B", 10, 120),
                ev("C", 20, 120),
                ev("D", 600, 720),
                ev("E", 610, 720),
                ev("F", 620, 720),
            ),
        )
        assertEquals(2, out.visible.size)
        assertEquals(2, out.overflow.size)
        // anchors stay in start-time order so the two chips place stably
        assertEquals(listOf(0L, 600L * 60_000), out.overflow.map { it.clusterStartMillis })
    }
}
