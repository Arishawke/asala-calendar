/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

// keep the collector warm briefly so a quick return skips a re-query.
internal const val UiStateStopTimeoutMillis = 5_000L

// shared uiState tail for the per-view event ViewModels (day / week / month /
// 3-day / mini-month / year): keep the `today` field fresh when the day rolls
// over, run the upstream filter/group transform off the main thread (it is not
// trivial on a large calendar), and share while subscribed. de-dupes the
// byte-identical tail those six carried (audit F9 / F10 / D4).
internal fun <S> Flow<S>.stateInWithToday(
    scope: CoroutineScope,
    todayFlow: StateFlow<LocalDate>,
    initial: S,
    currentToday: (S) -> LocalDate,
    withToday: (S, LocalDate) -> S,
): StateFlow<S> =
    combine(todayFlow) { state, today -> if (currentToday(state) == today) state else withToday(state, today) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(UiStateStopTimeoutMillis), initial)
