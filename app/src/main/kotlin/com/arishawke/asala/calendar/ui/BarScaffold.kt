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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arishawke.asala.calendar.ui.settings.ToolbarPosition

// shared scaffold for the secondary screens (Search, Event edit, Settings).
// places the single bar at the top or bottom per the toolbar-position setting
// and keeps content clear of the keyboard. the app is edge-to-edge with
// adjustResize, so the ime arrives as an inset that content must consume:
// the top branch lifts the whole scaffold with imePadding; the bottom branch
// folds ime into the bar's own inset (union with nav bar) so the bar sits
// flush above the keyboard and Scaffold reserves that height for content.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarScaffold(
    bar: @Composable (WindowInsets) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    when (LocalToolbarPosition.current) {
        ToolbarPosition.Top -> Scaffold(
            modifier = modifier.imePadding(),
            topBar = { bar(TopAppBarDefaults.windowInsets) },
            content = content,
        )
        ToolbarPosition.Bottom -> {
            val barInsets = WindowInsets.navigationBars
                .union(WindowInsets.ime)
                .only(WindowInsetsSides.Bottom)
            Scaffold(
                modifier = modifier,
                bottomBar = { bar(barInsets) },
                content = content,
            )
        }
    }
}
