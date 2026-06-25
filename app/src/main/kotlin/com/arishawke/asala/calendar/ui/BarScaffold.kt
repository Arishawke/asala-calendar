/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arishawke.asala.calendar.ui.settings.ToolbarPosition

// shared scaffold for the secondary screens (Search, Event edit, Settings).
// places the single bar at the top or bottom per the toolbar-position setting
// and hands it the right window insets: status bar on top; nav bar on bottom
// so a bottom bar clears the system nav. imePadding on the bottom branch lifts
// the whole scaffold (bar included) above the keyboard when a field is focused.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarScaffold(
    bar: @Composable (WindowInsets) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    when (LocalToolbarPosition.current) {
        ToolbarPosition.Top -> Scaffold(
            modifier = modifier,
            topBar = { bar(TopAppBarDefaults.windowInsets) },
            content = content,
        )
        ToolbarPosition.Bottom -> Scaffold(
            modifier = modifier.imePadding(),
            bottomBar = { bar(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)) },
            content = content,
        )
    }
}
