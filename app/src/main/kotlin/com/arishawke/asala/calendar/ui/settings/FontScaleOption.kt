/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.settings

// in-app text-size multiplier, composed on top of the OS font scale (see
// App.kt's LocalDensity seam). Default must stay factor 1.0 so an untouched
// install renders exactly as it did before this preference existed.
enum class FontScaleOption(val percent: Int, val factor: Float) {
    Small(percent = 85, factor = 0.85f),
    Default(percent = 100, factor = 1.0f),
    Large(percent = 115, factor = 1.15f),
    ExtraLarge(percent = 130, factor = 1.3f),
    Huge(percent = 150, factor = 1.5f),
}
