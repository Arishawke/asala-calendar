/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.multidaybars

// Greedy lane (row-within-week) assignment. Longest-span first, ties
// broken by earlier start. For each segment we pick the lowest lane
// index where no previously-assigned segment overlaps. Standard
// interval-graph coloring pattern.
object LaneAssigner {
    fun assignLanes(segments: List<WeekSegment>): List<WeekSegment> {
        if (segments.isEmpty()) return segments
        val sorted = segments.sortedWith(
            compareByDescending<WeekSegment> { it.spanDays }
                .thenBy { it.startCol }
                .thenBy { it.eventId },
        )
        // For each lane, track the per-column occupancy as a bitmask
        // across the week (7 bits). A new segment claims the lowest
        // lane whose bitmask has no overlap with the segment's range.
        val laneMasks = mutableListOf<Int>()
        val out = ArrayList<WeekSegment>(sorted.size)
        for (s in sorted) {
            val mask = rangeMask(s.startCol, s.endCol)
            var lane = laneMasks.indexOfFirst { (it and mask) == 0 }
            if (lane == -1) {
                lane = laneMasks.size
                laneMasks.add(0)
            }
            laneMasks[lane] = laneMasks[lane] or mask
            out.add(s.copy(lane = lane))
        }
        return out
    }

    private fun rangeMask(startCol: Int, endCol: Int): Int {
        var mask = 0
        for (c in startCol..endCol) mask = mask or (1 shl c)
        return mask
    }
}
