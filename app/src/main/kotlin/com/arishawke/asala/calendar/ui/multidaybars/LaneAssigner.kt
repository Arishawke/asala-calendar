/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.multidaybars

// greedy interval-graph coloring into lanes: longest-span first (ties by start), lowest non-overlapping lane
object LaneAssigner {
    fun assignLanes(segments: List<WeekSegment>): List<WeekSegment> {
        if (segments.isEmpty()) return segments
        val sorted = segments.sortedWith(
            compareByDescending<WeekSegment> { it.spanDays }
                .thenBy { it.startCol }
                .thenBy { it.eventId },
        )
        // per-lane occupancy as a 7-bit (one per weekday) mask; claim the lowest lane with no overlap
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
