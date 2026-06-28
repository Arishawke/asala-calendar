/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit.naturallanguage

import com.arishawke.asala.calendar.ui.theme.timeFormatter
import java.time.format.DateTimeFormatter
import java.util.Locale

// one-line "here is what I understood" summary for the Quick add field. null
// when only a title (or nothing) was recognized: there is nothing to preview.
internal fun previewLine(parsed: ParsedEvent, is24Hour: Boolean, locale: Locale): String? {
    if (parsed.date == null && parsed.startTime == null && parsed.location == null) return null
    val parts = mutableListOf<String>()
    parsed.date?.let { parts += it.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale)) }
    if (parsed.startTime != null) {
        val tf = timeFormatter(is24Hour, locale)
        // en-dash matches the time_range_format resource used elsewhere.
        parts += parsed.startTime.format(tf) + (parsed.endTime?.let { " – ${it.format(tf)}" }.orEmpty())
    }
    parsed.location?.let { parts += it }
    return parts.joinToString("  ·  ") // interpunct separator
}
