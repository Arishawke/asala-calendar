/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

// Identity of the local Asala calendar Asala Calendar creates and owns.
// One source of truth so every code path (onboarding gate, settings,
// repository) refers to the same account row in the provider.
object LocalCalendar {
    const val AccountName = "Asala Local"
    const val DisplayName = "Asala"

    // Okabe-Ito grey, matching OkabeItoPalette[0]. Held as a raw int here
    // so data-layer callers do not depend on the ui.theme package.
    const val DefaultColor: Int = 0xFF999999.toInt()
}
