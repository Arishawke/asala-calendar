/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDate

// shared "today" source so the highlight refreshes on midnight cross, clock
// change, or tz shift. without it each screen captures LocalDate.now() once at
// construction and stays on yesterday until the next process start.
// pure StateFlow holder (BroadcastReceiver wiring lives on the Application)
// so JVM tests can drive it with a synthetic Clock.
class TodayProvider(private val clock: Clock = Clock.systemDefaultZone()) {
    private val _today = MutableStateFlow(LocalDate.now(clock))
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    // called on DATE/TIME/TIMEZONE_CHANGED broadcasts and on ON_RESUME
    // (fallback for broadcasts missed under doze / battery-saver).
    fun refresh() {
        val now = LocalDate.now(clock)
        if (_today.value != now) _today.value = now
    }
}
