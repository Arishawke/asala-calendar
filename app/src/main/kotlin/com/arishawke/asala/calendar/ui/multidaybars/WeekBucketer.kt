/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.multidaybars

import com.arishawke.asala.calendar.data.EventItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private const val DaysPerWeek = 7
private const val LastDayCol = DaysPerWeek - 1

// slices all-day events into per-week segments. single-day ones are skipped by default (Month renders
// them inline; reserving a lane would push chips down); includeSingleDay opts back in (Week's AllDayRow).
// all-day endMillis is start-of-day after the last visible day in UTC, so dates
// are derived in UTC, not the device zone.
object WeekBucketer {
    fun bucketize(
        events: List<EventItem>,
        weekStart: LocalDate,
        zone: ZoneId,
        includeSingleDay: Boolean = false,
    ): List<WeekSegment> {
        val weekEnd = weekStart.plusDays((DaysPerWeek - 1).toLong())
        return events
            .filter { it.allDay }
            .mapNotNull { e -> segmentFor(e, weekStart, weekEnd, zone, includeSingleDay) }
    }

    @Suppress("UnusedParameter") // zone reserved for timed events if support is added later
    private fun segmentFor(
        e: EventItem,
        weekStart: LocalDate,
        weekEnd: LocalDate,
        zone: ZoneId,
        includeSingleDay: Boolean,
    ): WeekSegment? {
        val effectiveZone = ZoneOffset.UTC
        val firstVisible = Instant.ofEpochMilli(e.startMillis).atZone(effectiveZone).toLocalDate()
        val lastVisible = Instant.ofEpochMilli(e.endMillis).atZone(effectiveZone).toLocalDate().minusDays(1)
        // drop events outside the week, and single-day ones unless opted in
        val skip = (!includeSingleDay && firstVisible == lastVisible) ||
            lastVisible.isBefore(weekStart) ||
            firstVisible.isAfter(weekEnd)
        if (skip) return null
        val clippedStart = if (firstVisible.isBefore(weekStart)) weekStart else firstVisible
        val clippedEnd = if (lastVisible.isAfter(weekEnd)) weekEnd else lastVisible
        val startCol = (clippedStart.toEpochDay() - weekStart.toEpochDay()).toInt().coerceIn(0, LastDayCol)
        val endCol = (clippedEnd.toEpochDay() - weekStart.toEpochDay()).toInt().coerceIn(0, LastDayCol)
        return WeekSegment(
            eventId = e.eventId,
            title = e.title,
            color = e.displayColor,
            startCol = startCol,
            endCol = endCol,
            isContinuedLeft = firstVisible.isBefore(weekStart),
            isContinuedRight = lastVisible.isAfter(weekEnd),
            isBirthday = e.isBirthday,
        )
    }
}
