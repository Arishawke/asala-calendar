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

// Shared fling spec for Day / Week / Month pagers. Stiff no-bouncy spring +
// 0.35 positional threshold is the community-converged recipe for a
// snappy follow-finger snap. Tween-based specs trade physics for
// time-predictability; spring feels snappier on a follow-finger drag.
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
