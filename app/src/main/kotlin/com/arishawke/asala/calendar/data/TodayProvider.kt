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

// Shared "today" source. Each ViewModel + the AppShell collect this so
// the today-highlight refreshes when local midnight crosses, when the
// user changes the device clock, or when the timezone shifts. Without
// this, every screen captures LocalDate.now() once at construction and
// the highlight stays on yesterday until the next process start.
//
// The BroadcastReceiver wiring lives on AsalaCalendarApplication (which
// owns the Context lifetime); this class is a pure-Kotlin StateFlow
// holder so JVM unit tests can exercise it with a synthetic Clock.
class TodayProvider(private val clock: Clock = Clock.systemDefaultZone()) {
    private val _today = MutableStateFlow(LocalDate.now(clock))
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    // Called by the Application's BroadcastReceiver on
    // ACTION_DATE_CHANGED / ACTION_TIME_CHANGED / ACTION_TIMEZONE_CHANGED,
    // and by the AppShell on ON_RESUME (belt-and-braces for missed
    // broadcasts under doze / battery-saver).
    fun refresh() {
        val now = LocalDate.now(clock)
        if (_today.value != now) _today.value = now
    }
}
