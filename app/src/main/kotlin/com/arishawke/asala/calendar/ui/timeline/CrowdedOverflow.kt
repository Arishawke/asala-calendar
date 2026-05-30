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

// At or above this many simultaneously overlapping events, a cluster's
// columns get too thin to read in Week, so all but column 0 collapse
// into a single overflow group. Day view passes Int.MAX_VALUE to opt out.
internal const val CrowdedColumnThreshold = 3

internal data class OverflowGroup(val collapsedCount: Int, val events: List<EventItem>, val clusterStartMillis: Long)

internal data class CrowdedLayout(val visible: List<LaidOutEvent>, val overflow: List<OverflowGroup>)

internal fun crowdedLayout(events: List<DayClippedEvent>, threshold: Int = CrowdedColumnThreshold): CrowdedLayout {
    val laid = layOutOverlaps(events)
    val visible = ArrayList<LaidOutEvent>(laid.size)
    val overflow = ArrayList<OverflowGroup>()
    laid.groupBy { it.clusterIndex }.forEach { (_, cluster) ->
        val width = cluster.first().clusterWidth
        if (width < threshold) {
            visible += cluster
        } else {
            visible += cluster.filter { it.columnIndex == 0 }
            val collapsed = cluster.filter { it.columnIndex != 0 }
            overflow += OverflowGroup(
                collapsedCount = collapsed.size,
                events = cluster.map { it.clipped.event },
                clusterStartMillis = cluster.minOf { it.clipped.displayStartMillis },
            )
        }
    }
    return CrowdedLayout(visible = visible, overflow = overflow)
}
