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

// animated height in px of the header panel expanding above a bottom
// toolbar, 0 whenever the toolbar is on top. provided at the shell root;
// defaults to a constant 0 in previews / tests.
val LocalBottomPanelIntrusion: ProvidableCompositionLocal<State<Int>> =
    compositionLocalOf { mutableIntStateOf(0) }

// scrolls by each frame's intrusion delta so the visible rows stay anchored
// to the rising / falling panel edge. top mode pushes content down by
// growing top padding, which visibly translates the same rows; a bottom
// panel only crops a top-anchored scrollable, reading as an overlay. this
// is the bottom-mode mirror of that push. deltas start from the value at
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
