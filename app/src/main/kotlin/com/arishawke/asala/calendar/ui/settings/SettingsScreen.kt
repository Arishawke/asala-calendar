/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arishawke.asala.calendar.DrawerHiddenAccount
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.BarScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    drawerHiddenAccounts: List<DrawerHiddenAccount> = emptyList(),
    onRestoreAccountToDrawer: (accountKey: String) -> Unit = {},
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(ctx.applicationContext))
    val s by vm.state.collectAsStateWithLifecycle()
    // lazy list state so scroll survives the recompose from permission
    // callbacks; the prior verticalScroll bounced to top each recompose.
    val listState = rememberLazyListState()

    BarScaffold(
        bar = { insets ->
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
                windowInsets = insets,
            )
        },
    ) { padding ->
        // each section is a collapsible disclosure; the screen opens with General
        // expanded and the rest collapsed so it stays short and scannable.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item("section-general") {
                CollapsibleSection(
                    stringResource(R.string.settings_section_general),
                    initiallyExpanded = true,
                ) {
                    GeneralSettings(s, vm)
                }
            }
            item("section-appearance") {
                CollapsibleSection(stringResource(R.string.settings_section_appearance)) {
                    AppearanceSettings(s, vm)
                }
            }
            item("section-notifications") {
                CollapsibleSection(stringResource(R.string.settings_notifications_header)) {
                    NotificationsSettings(s, vm, notificationPermissionGranted, onRequestNotificationPermission)
                }
            }
            item("section-widgets") {
                CollapsibleSection(stringResource(R.string.settings_section_widgets)) {
                    WidgetsSettings(s, vm)
                }
            }
            item("section-calendars") {
                CollapsibleSection(stringResource(R.string.settings_section_calendars_and_accounts)) {
                    CalendarsSettings(s, vm, drawerHiddenAccounts, onRestoreAccountToDrawer)
                }
            }
            item("section-about") {
                CollapsibleSection(stringResource(R.string.settings_section_about)) {
                    AboutSettings()
                }
            }
        }
    }
}
