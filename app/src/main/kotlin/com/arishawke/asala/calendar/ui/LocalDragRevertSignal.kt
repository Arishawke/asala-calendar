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

// event ids whose in-flight drag offset should drop (e.g. scope picker
// cancelled). a CompositionLocal so deep chips subscribe without
// prop-drilling. null in previews / tests.
val LocalDragRevertSignal: ProvidableCompositionLocal<SharedFlow<Long>?> = compositionLocalOf { null }
