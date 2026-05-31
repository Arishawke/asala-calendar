/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arishawke.asala.calendar.DrawerHiddenAccount
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.notifications.OemBatteryAdvisory
import com.arishawke.asala.calendar.ui.theme.LocalIs24Hour
import com.arishawke.asala.calendar.ui.theme.Spacing

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

    Scaffold(
        topBar = {
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
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item("section-general") {
                SectionHeader(stringResource(R.string.settings_section_general))
            }
            item("general-default-view") {
                DefaultViewRow(
                    current = s.defaultView,
                    tasksEnabled = s.tasksEnabled,
                    onChange = vm::setDefaultView,
                )
            }
            item("general-week-starts") {
                WeekStartsOnRow(s.weekStartsOn, vm::setWeekStartsOn)
            }
            item("general-default-duration") {
                DefaultDurationDropdown(
                    current = s.defaultDurationMinutes,
                    onChange = vm::setDefaultDurationMinutes,
                )
            }
            item("general-time-format") {
                TimeFormatRow(
                    current = s.is24HourOverride,
                    onChange = vm::setIs24HourOverride,
                )
            }
            item("general-tasks-enabled") {
                SwitchRow(
                    label = stringResource(R.string.settings_tasks_enabled),
                    checked = s.tasksEnabled,
                    onChange = vm::setTasksEnabled,
                    supporting = stringResource(R.string.settings_tasks_enabled_summary),
                )
            }

            item("section-appearance") {
                SectionHeader(stringResource(R.string.settings_section_appearance))
            }
            item("appearance-theme") { ThemeRow(s.themeMode, vm::setTheme) }
            item("appearance-palette") { PaletteRow(s.paletteId, vm::setPaletteId) }
            item("appearance-dim-past") {
                SwitchRow(
                    label = stringResource(R.string.settings_dim_past_dates),
                    checked = s.dimPastDates,
                    onChange = vm::setDimPastDates,
                )
            }
            item("appearance-working-hours-toggle") {
                SwitchRow(
                    label = stringResource(R.string.settings_working_hours_label),
                    checked = s.workingHoursEnabled,
                    onChange = vm::setWorkingHoursEnabled,
                    supporting = stringResource(R.string.settings_working_hours_supporting),
                )
            }
            if (s.workingHoursEnabled) {
                item("appearance-working-hours-range") {
                    WorkingHoursRangeRow(
                        startHour = s.workingHoursStartHour,
                        endHour = s.workingHoursEndHour,
                        is24Hour = LocalIs24Hour.current,
                        onChange = vm::setWorkingHoursRange,
                    )
                }
            }
            item("appearance-working-days-toggle") {
                SwitchRow(
                    label = stringResource(R.string.settings_working_days_label),
                    checked = s.workingDaysEnabled,
                    onChange = vm::setWorkingDaysEnabled,
                    supporting = stringResource(R.string.settings_working_days_supporting),
                )
            }
            if (s.workingDaysEnabled) {
                item("appearance-working-days-picker") {
                    WorkingDaysRow(
                        workingDays = s.workingDays,
                        firstDayOfWeek = s.weekStartsOn,
                        onChange = vm::setWorkingDays,
                    )
                }
            }
            item("appearance-show-week-number") {
                SwitchRow(
                    label = stringResource(R.string.settings_show_week_number_label),
                    checked = s.showWeekNumber,
                    onChange = vm::setShowWeekNumber,
                    supporting = stringResource(R.string.settings_show_week_number_supporting),
                )
            }
            item("appearance-month-scroll-style") {
                MonthScrollStyleRow(
                    current = s.monthScrollStyle,
                    onChange = vm::setMonthScrollStyle,
                )
            }

            item("section-notifications") {
                SectionHeader(stringResource(R.string.settings_notifications_header))
            }
            item("notif-permission-status") {
                ListItem(
                    headlineContent = {
                        Text(
                            if (notificationPermissionGranted) {
                                stringResource(R.string.settings_notifications_status_on)
                            } else {
                                stringResource(R.string.settings_notifications_status_off)
                            },
                        )
                    },
                    supportingContent = if (notificationPermissionGranted) {
                        null
                    } else {
                        { Text(stringResource(R.string.settings_notifications_permission_supporting)) }
                    },
                    modifier = Modifier.then(
                        if (!notificationPermissionGranted) {
                            Modifier.clickable { onRequestNotificationPermission() }
                        } else {
                            Modifier
                        },
                    ),
                )
            }
            item("notif-default-snooze") {
                DefaultSnoozeDropdown(
                    current = s.defaultSnoozeMinutes,
                    onChange = vm::setDefaultSnoozeMinutes,
                )
            }
            item("notif-default-reminder-timed") {
                DefaultReminderRow(
                    labelResId = R.string.settings_default_reminder_timed,
                    current = s.defaultTimedReminderMinutes,
                    onChange = vm::setDefaultTimedReminderMinutes,
                )
            }
            item("notif-default-reminder-allday") {
                DefaultReminderRow(
                    labelResId = R.string.settings_default_reminder_all_day,
                    current = s.defaultAllDayReminderMinutes,
                    onChange = vm::setDefaultAllDayReminderMinutes,
                )
            }
            if (OemBatteryAdvisory.isAffected()) {
                item("notif-oem-advisory") {
                    val context = LocalContext.current
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_background_reliability)) },
                        supportingContent = { Text(stringResource(R.string.settings_background_reliability_sub)) },
                        modifier = Modifier.clickable {
                            context.startActivity(OemBatteryAdvisory.batterySettingsIntent(context))
                        },
                    )
                }
            }

            item("section-calendars") {
                SectionHeader(stringResource(R.string.settings_section_calendars_and_accounts))
            }
            // shown only when non-empty so the section never clutters
            // settings for users who never hide an account.
            if (drawerHiddenAccounts.isNotEmpty()) {
                item("calendars-hidden-header") {
                    Text(
                        text = stringResource(R.string.settings_section_hidden_accounts),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = Spacing.lg,
                            top = Spacing.sm,
                            bottom = Spacing.xs,
                        ),
                    )
                }
                items(
                    items = drawerHiddenAccounts,
                    key = { acc -> "hidden-${acc.accountKey}" },
                ) { account ->
                    ListItem(
                        headlineContent = {
                            Text(account.accountName.ifBlank { account.accountKey })
                        },
                        trailingContent = {
                            Text(
                                text = stringResource(R.string.action_show_in_drawer),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable { onRestoreAccountToDrawer(account.accountKey) },
                    )
                }
            }
            item("calendars-davx5") {
                ClickRow(
                    title = stringResource(R.string.settings_davx5_title),
                    supporting = stringResource(R.string.settings_davx5_supporting),
                    onClick = { openDavx5(ctx) },
                )
            }
            item("calendars-storage-mode") {
                StorageModeRow(s.storageMode, vm::setStorageMode)
            }

            item("section-about") {
                SectionHeader(stringResource(R.string.settings_section_about))
            }
            item("about") {
                val context = LocalContext.current
                val sourceUrl = stringResource(R.string.settings_about_source_url)
                val licensesUrl = stringResource(R.string.settings_about_licenses_url)
                val supportUrl = stringResource(R.string.settings_about_support_url)
                AboutSection(
                    onOpenSource = { context.startActivity(Intent(Intent.ACTION_VIEW, sourceUrl.toUri())) },
                    onOpenLicenses = { context.startActivity(Intent(Intent.ACTION_VIEW, licensesUrl.toUri())) },
                    onOpenSupport = { context.startActivity(Intent(Intent.ACTION_VIEW, supportUrl.toUri())) },
                )
            }
        }
    }
}
