/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

internal data class LaidOutEvent(
    val clipped: DayClippedEvent,
    val columnIndex: Int,
    val clusterWidth: Int,
    val clusterIndex: Int = 0,
)

internal fun layOutOverlaps(events: List<DayClippedEvent>): List<LaidOutEvent> {
    if (events.isEmpty()) return emptyList()

    val sorted = events.sortedWith(
        compareBy<DayClippedEvent> { it.displayStartMillis }.thenBy { it.event.title },
    )

    val out = ArrayList<LaidOutEvent>(sorted.size)
    val clusterStart = ArrayList<LaidOutEvent>()
    val columnEnds = ArrayList<Long>()
    var clusterMaxEnd = Long.MIN_VALUE
    var clusterIndex = 0

    fun flushCluster() {
        val width = columnEnds.size.coerceAtLeast(1)
        clusterStart.forEach { laid ->
            out += laid.copy(clusterWidth = width)
        }
        clusterStart.clear()
        columnEnds.clear()
        clusterMaxEnd = Long.MIN_VALUE
        clusterIndex++
    }

    for (ev in sorted) {
        if (ev.displayStartMillis >= clusterMaxEnd && clusterStart.isNotEmpty()) {
            flushCluster()
        }
        var col = columnEnds.indexOfFirst { it <= ev.displayStartMillis }
        if (col == -1) {
            col = columnEnds.size
            columnEnds.add(ev.displayEndMillis)
        } else {
            columnEnds[col] = ev.displayEndMillis
        }
        clusterStart += LaidOutEvent(ev, col, clusterWidth = 0, clusterIndex = clusterIndex)
        if (ev.displayEndMillis > clusterMaxEnd) clusterMaxEnd = ev.displayEndMillis
    }
    flushCluster()
    return out
}
