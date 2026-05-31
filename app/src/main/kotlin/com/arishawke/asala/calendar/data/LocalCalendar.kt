/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

// single source of truth for the local calendar's provider account row.
object LocalCalendar {
    const val AccountName = "Asala Local"
    const val DisplayName = "Asala"

    // Okabe-Ito grey (OkabeItoPalette[0]); raw int so the data layer
    // doesn't depend on ui.theme.
    const val DefaultColor: Int = 0xFF999999.toInt()
}
