/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.arishawke.asala.calendar.ui.settings.ToolbarPosition

// the app bar position, provided once at the root so every screen's bar
// reads it without prop-drilling. defaults Top in previews / tests.
val LocalToolbarPosition: ProvidableCompositionLocal<ToolbarPosition> =
    compositionLocalOf { ToolbarPosition.Top }
