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
import kotlinx.coroutines.flow.SharedFlow

// Ambient SharedFlow of event ids that should drop their in-flight drag
// offset (e.g. user cancelled the recurring scope picker after a drag
// release). Provided at the AppShell level so the deep chip composables
// can subscribe without prop-drilling the signal through every layer.
// Null in non-production composition contexts (previews / tests).
val LocalDragRevertSignal: ProvidableCompositionLocal<SharedFlow<Long>?> = compositionLocalOf { null }
