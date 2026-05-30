/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui

import android.text.format.DateFormat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arishawke.asala.calendar.AppViewModel
import com.arishawke.asala.calendar.ui.permissions.CalendarPermissionGate
import com.arishawke.asala.calendar.ui.settings.ThemeMode
import com.arishawke.asala.calendar.ui.theme.AsalaCalendarTheme
import com.arishawke.asala.calendar.ui.theme.LocalIs24Hour

// Top-level Compose entry. Collects only the bits needed before the
// calendar permission has been granted (theme and 24h preference);
// AppShell behind the permission gate subscribes to the full uiState
// and the ContentObserver it carries.
@Composable
internal fun App() {
    val context = LocalContext.current
    val vm: AppViewModel = viewModel(
        factory = AppViewModel.Factory(context.applicationContext),
    )
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()

    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.Amoled -> true
    }
    val amoled = themeMode == ThemeMode.Amoled

    // The Android system 24-hour setting is not part of Configuration, so
    // Compose does not auto-recompose when the user toggles it system-side.
    // Re-read on ON_RESUME so a setting change while the app was backgrounded
    // is reflected the next time the user returns.
    var systemIs24Hour by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemIs24Hour = DateFormat.is24HourFormat(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val resolvedIs24Hour = prefs.is24HourOverride ?: systemIs24Hour

    AsalaCalendarTheme(darkTheme = darkTheme, amoled = amoled) {
        CompositionLocalProvider(LocalIs24Hour provides resolvedIs24Hour) {
            CalendarPermissionGate {
                AppShell(vm = vm)
            }
        }
    }
}
