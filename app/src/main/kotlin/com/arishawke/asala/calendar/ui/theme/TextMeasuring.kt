/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp

// widest-of-N-candidates text width at the current density/fontScale, memoized
// so a scale or locale change re-measures but an unrelated recomposition does
// not. for containers sized to fit worst-case content (hour labels, schedule
// day/time columns) instead of a fixed dp guess.
// suppress: List<String> is flagged non-stable, but candidates is built once
// per locale/scale change by the caller's own remember, not per frame.
// cheaper than pulling in kotlinx-collections-immutable.
@Suppress("ComposeUnstableCollections")
@Composable
internal fun rememberWidestTextWidth(candidates: List<String>, style: TextStyle): Dp {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    return remember(candidates, style, density) {
        val widestPx = candidates.maxOf { candidate ->
            textMeasurer.measure(text = AnnotatedString(candidate), style = style).size.width
        }
        with(density) { widestPx.toDp() }
    }
}
