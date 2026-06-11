/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit.naturallanguage

import java.time.LocalDate
import java.time.LocalTime

// recognized fields from a typed phrase. all optional except title; null means
// "not recognized, leave the editor's default".
data class ParsedEvent(
    val title: String,
    val location: String? = null,
    val date: LocalDate? = null,
    val startTime: LocalTime? = null, // null + date present -> all-day
    val endTime: LocalTime? = null,
)
