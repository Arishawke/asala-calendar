/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

import com.arishawke.asala.calendar.data.TimeUnits
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class RevealEdge { Above, Visible, Below }

// top pixel of a time-of-day given the per-hour height.
internal fun revealTargetPx(time: LocalTime, hourHeightPx: Float): Int =
    ((time.hour + time.minute / TimeUnits.MinutesPerHour.toFloat()) * hourHeightPx).toInt()

// where the target sits vs the current scroll window. margin keeps a target
// that is only a sliver into view from counting as fully visible.
internal fun revealEdge(targetPx: Int, scrollPx: Int, viewportPx: Int, marginPx: Int): RevealEdge {
    val top = scrollPx + marginPx
    val bottom = scrollPx + viewportPx - marginPx
    return when {
        targetPx < top -> RevealEdge.Above
        targetPx > bottom -> RevealEdge.Below
        else -> RevealEdge.Visible
    }
}

// locale drives the AM/PM marker on 12-hour patterns, matching HourAxis.
internal fun formatRevealTime(time: LocalTime, is24Hour: Boolean, locale: Locale): String =
    time.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale))
