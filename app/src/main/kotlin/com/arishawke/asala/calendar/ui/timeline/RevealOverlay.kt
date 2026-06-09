/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.PendingEventReveal
import com.arishawke.asala.calendar.ui.theme.LocalIs24Hour
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RevealPillTimeoutMs = 5_000L
private val RevealMargin = 24.dp

// `reveal` is pre-filtered by the screen to this view and a date on this page.
@Suppress("LongParameterList")
@Composable
internal fun BoxScope.RevealOverlay(
    reveal: PendingEventReveal?,
    scrollState: ScrollState,
    viewportHeightPx: Int,
    hourHeightPx: Float,
    onHighlight: (Long) -> Unit,
    onConsume: () -> Unit,
) {
    val time = reveal?.time ?: return // all-day or none: nothing to point at
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val targetPx = remember(time, hourHeightPx) { revealTargetPx(time, hourHeightPx) }
    val marginPx = remember(density) { with(density) { RevealMargin.toPx().toInt() } }
    // reads scrollState.value so the edge (and thus the pill) updates live as
    // the timeline scrolls; the pill dismisses once the event scrolls in.
    val edge by remember(targetPx, viewportHeightPx, marginPx) {
        derivedStateOf { revealEdge(targetPx, scrollState.value, viewportHeightPx, marginPx) }
    }

    // already on screen: glow and consume, no pill.
    LaunchedEffect(reveal, edge) {
        if (edge == RevealEdge.Visible) {
            onHighlight(reveal.eventId)
            onConsume()
        }
    }

    // an ignored pill clears itself after a few seconds.
    LaunchedEffect(reveal) {
        delay(RevealPillTimeoutMs)
        onConsume()
    }

    val is24Hour = LocalIs24Hour.current
    val timeText = remember(time, is24Hour) { formatRevealTime(time, is24Hour) }
    AnimatedVisibility(
        visible = edge != RevealEdge.Visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = if (edge == RevealEdge.Above) {
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
        } else {
            Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp) // clears the create FAB
        },
    ) {
        OffScreenEventPill(
            edge = edge,
            timeText = timeText,
            onClick = {
                scope.launch {
                    scrollState.animateScrollTo(targetPx)
                    onHighlight(reveal.eventId)
                    onConsume()
                }
            },
        )
    }
}
