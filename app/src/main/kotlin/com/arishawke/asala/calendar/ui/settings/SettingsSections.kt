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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.arishawke.asala.calendar.DrawerHiddenAccount
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.notifications.OemBatteryAdvisory
import com.arishawke.asala.calendar.ui.permissions.rememberContactsPermissionRequest
import com.arishawke.asala.calendar.ui.theme.LocalIs24Hour
import com.arishawke.asala.calendar.ui.theme.Spacing

// each section's rows, lifted out of SettingsScreen so the screen stays a short
// list of collapsible headers and each section keeps its nesting shallow.

@Composable
internal fun GeneralSettings(s: UserPrefs, vm: SettingsViewModel) {
    DefaultViewRow(current = s.defaultView, onChange = vm::setDefaultView)
    MonthScrollStyleRow(current = s.monthScrollStyle, onChange = vm::setMonthScrollStyle)
    WeekStartsOnRow(s.weekStartsOn, vm::setWeekStartsOn)
    DefaultDurationDropdown(current = s.defaultDurationMinutes, onChange = vm::setDefaultDurationMinutes)
    TimeFormatRow(current = s.is24HourOverride, onChange = vm::setIs24HourOverride)
}

@Composable
internal fun AppearanceSettings(s: UserPrefs, vm: SettingsViewModel) {
    ThemeRow(s.themeMode, vm::setTheme)
    PaletteRow(s.paletteId, vm::setPaletteId)
    ToolbarPositionRow(current = s.toolbarPosition, onChange = vm::setToolbarPosition)
    SwitchRow(
        label = stringResource(R.string.settings_dim_past_dates),
        checked = s.dimPastDates,
        onChange = vm::setDimPastDates,
    )
    SwitchRow(
        label = stringResource(R.string.settings_working_hours_label),
        checked = s.workingHoursEnabled,
        onChange = vm::setWorkingHoursEnabled,
        supporting = stringResource(R.string.settings_working_hours_supporting),
    )
    if (s.workingHoursEnabled) {
        WorkingHoursRangeRow(
            startHour = s.workingHoursStartHour,
            endHour = s.workingHoursEndHour,
            is24Hour = LocalIs24Hour.current,
            onChange = vm::setWorkingHoursRange,
        )
    }
    SwitchRow(
        label = stringResource(R.string.settings_working_days_label),
        checked = s.workingDaysEnabled,
        onChange = vm::setWorkingDaysEnabled,
        supporting = stringResource(R.string.settings_working_days_supporting),
    )
    if (s.workingDaysEnabled) {
        WorkingDaysRow(workingDays = s.workingDays, firstDayOfWeek = s.weekStartsOn, onChange = vm::setWorkingDays)
    }
    SwitchRow(
        label = stringResource(R.string.settings_show_week_number_label),
        checked = s.showWeekNumber,
        onChange = vm::setShowWeekNumber,
        supporting = stringResource(R.string.settings_show_week_number_supporting),
    )
}

@Composable
internal fun WidgetsSettings(s: UserPrefs, vm: SettingsViewModel) {
    WidgetThemeRow(s.widgetThemeMode, vm::setWidgetThemeMode)
    SwitchRow(
        label = stringResource(R.string.settings_widget_translucent),
        checked = s.widgetTranslucent,
        onChange = vm::setWidgetTranslucent,
        supporting = stringResource(R.string.settings_widget_translucent_summary),
    )
}

@Composable
internal fun NotificationsSettings(
    s: UserPrefs,
    vm: SettingsViewModel,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
) {
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
        modifier = if (notificationPermissionGranted) {
            Modifier
        } else {
            Modifier.clickable { onRequestNotificationPermission() }
        },
    )
    DefaultSnoozeDropdown(current = s.defaultSnoozeMinutes, onChange = vm::setDefaultSnoozeMinutes)
    DefaultReminderRow(
        labelResId = R.string.settings_default_reminder_timed,
        current = s.defaultTimedReminderMinutes,
        onChange = vm::setDefaultTimedReminderMinutes,
    )
    DefaultReminderRow(
        labelResId = R.string.settings_default_reminder_all_day,
        current = s.defaultAllDayReminderMinutes,
        onChange = vm::setDefaultAllDayReminderMinutes,
    )
    if (s.contactOccasionsEnabled) {
        DefaultReminderRow(
            labelResId = R.string.settings_contact_reminder,
            current = s.contactReminderMinutesBefore,
            onChange = vm::setContactReminderMinutesBefore,
        )
    }
    if (OemBatteryAdvisory.isAffected()) {
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

// suppress: List<DrawerHiddenAccount> trips ComposeUnstableCollections; settings
// is not a hot recomposition path and this list is small and rarely changes, so
// the stability hint does not matter here.
@Suppress("ComposeUnstableCollections")
@Composable
internal fun CalendarsSettings(
    s: UserPrefs,
    vm: SettingsViewModel,
    drawerHiddenAccounts: List<DrawerHiddenAccount>,
    onRestoreAccountToDrawer: (accountKey: String) -> Unit,
) {
    val ctx = LocalContext.current
    // shown only when non-empty so the section never clutters for users who
    // never hide an account.
    if (drawerHiddenAccounts.isNotEmpty()) {
        Text(
            text = stringResource(R.string.settings_section_hidden_accounts),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.xs),
        )
        drawerHiddenAccounts.forEach { account ->
            ListItem(
                headlineContent = { Text(account.accountName.ifBlank { account.accountKey }) },
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
    ClickRow(
        title = stringResource(R.string.settings_davx5_title),
        supporting = stringResource(R.string.settings_davx5_supporting),
        onClick = { openDavx5(ctx) },
    )
    StorageModeRow(s.storageMode, vm::setStorageMode)

    // off by default; turning it on requests READ_CONTACTS and only
    // provisions the two calendars on grant. denial leaves the toggle off.
    val requestContactsPermission = rememberContactsPermissionRequest(
        onGranted = vm::enableContactOccasions,
        onDenied = vm::disableContactOccasions,
    )
    SwitchRow(
        label = stringResource(R.string.settings_contact_occasions),
        checked = s.contactOccasionsEnabled,
        onChange = { enabled -> if (enabled) requestContactsPermission() else vm.disableContactOccasions() },
        supporting = stringResource(R.string.settings_contact_occasions_supporting),
    )
}

@Composable
internal fun AboutSettings() {
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
