/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

// the start/end clock fields the editor shows for an existing or duplicated event.
data class LocalRange(
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
)

// shared by the existing-event load path and forDuplicate. all-day rows are
// stored at UTC midnight, so extract in UTC or the date lands a day early in
// negative-offset zones; the display end date drops EventSave's exclusive +1
// day. a recurring row opened from an instance prefills that instance's slot,
// not the parent DTSTART, so instance/following edits land on the right day.
@Suppress("LongParameterList")
internal fun extractLocalRange(
    startMillis: Long,
    endMillis: Long,
    allDay: Boolean,
    rrule: String?,
    instanceStartMillis: Long?,
    zone: ZoneId,
): LocalRange {
    val effectiveStart = if (rrule != null && instanceStartMillis != null) instanceStartMillis else startMillis
    val effectiveEnd = effectiveStart + (endMillis - startMillis)
    val extractionZone = if (allDay) ZoneOffset.UTC else zone
    val sLocal = Instant.ofEpochMilli(effectiveStart).atZone(extractionZone).toLocalDateTime()
    val eLocal = Instant.ofEpochMilli(effectiveEnd).atZone(extractionZone).toLocalDateTime()
    val displayEndDate = if (allDay) eLocal.toLocalDate().minusDays(1) else eLocal.toLocalDate()
    return LocalRange(sLocal.toLocalDate(), sLocal.toLocalTime(), displayEndDate, eLocal.toLocalTime())
}
