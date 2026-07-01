/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.multidaybars

import org.junit.Assert.assertEquals
import org.junit.Test

class LaneAssignerTest {
    private fun seg(id: Long, startCol: Int, endCol: Int): WeekSegment = WeekSegment(
        eventId = id,
        calendarId = id,
        title = "e$id",
        color = 0,
        startCol = startCol,
        endCol = endCol,
        isContinuedLeft = false,
        isContinuedRight = false,
    )

    @Test
    fun `single segment gets lane 0`() {
        val out = LaneAssigner.assignLanes(listOf(seg(1L, 0, 6)))
        assertEquals(0, out.single().lane)
    }

    @Test
    fun `non-overlapping segments share lane 0`() {
        // Mon-Tue and Thu-Fri don't overlap, both go on lane 0.
        val out = LaneAssigner.assignLanes(
            listOf(
                seg(1L, 1, 2),
                seg(2L, 4, 5),
            ),
        )
        assertEquals(0, out.first { it.eventId == 1L }.lane)
        assertEquals(0, out.first { it.eventId == 2L }.lane)
    }

    @Test
    fun `overlapping segments get separate lanes`() {
        // Mon-Wed and Tue-Thu overlap; one goes lane 0, other lane 1.
        val out = LaneAssigner.assignLanes(
            listOf(
                seg(1L, 1, 3),
                seg(2L, 2, 4),
            ),
        )
        val lanes = out.map { it.lane }.toSet()
        assertEquals(setOf(0, 1), lanes)
    }

    @Test
    fun `longest span wins lane 0`() {
        // Sun-Sat (7 days) and Tue-Wed (2 days) overlap. Longest goes first.
        val out = LaneAssigner.assignLanes(
            listOf(
                seg(2L, 2, 3),
                seg(1L, 0, 6),
            ),
        )
        assertEquals(0, out.first { it.eventId == 1L }.lane)
        assertEquals(1, out.first { it.eventId == 2L }.lane)
    }

    @Test
    fun `lane assignment fills lowest free slot - lane 1 freed by non-overlap`() {
        // Three segments: A spans 0-3, B spans 2-5, C spans 4-6.
        // A is longest -> lane 0. B overlaps A -> lane 1. C does NOT overlap A,
        // so the lowest free lane for C is 0 (A's natural ends before C starts).
        val out = LaneAssigner.assignLanes(
            listOf(
                seg(1L, 0, 3),
                seg(2L, 2, 5),
                seg(3L, 4, 6),
            ),
        )
        assertEquals(0, out.first { it.eventId == 1L }.lane)
        assertEquals(1, out.first { it.eventId == 2L }.lane)
        assertEquals(0, out.first { it.eventId == 3L }.lane)
    }

    @Test
    fun `tie on span breaks by earlier start`() {
        // Two equal-length segments: earlier start gets the lower lane.
        val out = LaneAssigner.assignLanes(
            listOf(
                seg(2L, 4, 5),
                seg(1L, 0, 1),
            ),
        )
        assertEquals(0, out.first { it.eventId == 1L }.lane)
        assertEquals(0, out.first { it.eventId == 2L }.lane)
    }

    @Test
    fun `empty input returns empty output`() {
        assertEquals(emptyList<WeekSegment>(), LaneAssigner.assignLanes(emptyList()))
    }
}
