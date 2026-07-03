/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow

// animated px height of the header panel above a bottom toolbar; 0 with a
// top toolbar and in previews / tests.
val LocalBottomPanelIntrusion: ProvidableCompositionLocal<State<Int>> =
    compositionLocalOf { mutableIntStateOf(0) }

// follows the panel's animated height by delta so rows slide up with its
// edge, mirroring top mode's padding push. deltas start from the value at
// composition, so a page created under an open panel does not jump.
@Composable
internal fun CompensateBottomPanelIntrusion(scrollable: ScrollableState) {
    val intrusion = LocalBottomPanelIntrusion.current
    LaunchedEffect(scrollable, intrusion) {
        var prev = intrusion.value
        snapshotFlow { intrusion.value }.collect { now ->
            val delta = now - prev
            prev = now
            if (delta != 0) scrollable.scrollBy(delta.toFloat())
        }
    }
}
