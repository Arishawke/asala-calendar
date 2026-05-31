/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable

// shared Day/Week/Month fling. no-bouncy spring + 0.35 threshold is the
// community recipe for a snappy follow-finger snap; spring beats tween here.
@Composable
internal fun rememberCalendarPagerFling(state: PagerState): TargetedFlingBehavior = PagerDefaults.flingBehavior(
    state = state,
    snapAnimationSpec =
    spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    ),
    snapPositionalThreshold = 0.35f,
)
