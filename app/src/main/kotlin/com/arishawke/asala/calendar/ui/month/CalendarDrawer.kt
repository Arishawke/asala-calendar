/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month

import android.provider.CalendarContract
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.CalendarItem
import com.arishawke.asala.calendar.isAlwaysVisible
import com.arishawke.asala.calendar.label
import com.arishawke.asala.calendar.ui.calendars.DeleteCalendarDialog
import com.arishawke.asala.calendar.ui.calendars.RecolorDialog
import com.arishawke.asala.calendar.ui.calendars.RenameCalendarDialog
import com.arishawke.asala.calendar.ui.month.drawer.AccountGroup
import com.arishawke.asala.calendar.ui.month.drawer.AccountHeader
import com.arishawke.asala.calendar.ui.month.drawer.CalendarRow
import com.arishawke.asala.calendar.ui.month.drawer.accountOverrideKey
import com.arishawke.asala.calendar.ui.month.drawer.avatarColor
import com.arishawke.asala.calendar.ui.theme.PaletteId

@Composable
fun CalendarDrawerContent(
    currentView: CalendarView,
    onSelectView: (CalendarView) -> Unit,
    calendars: List<CalendarItem>,
    hiddenCalendarIds: Set<Long>,
    drawerHiddenAccountKeys: Set<String>,
    collapsedAccounts: Set<String>,
    // Lookup function rather than a Map so the @Composable parameter list
    // stays stable for Compose (Map<String, Int> trips ComposeUnstableCollections).
    avatarOverrideFor: (accountKey: String) -> Int?,
    onToggle: (Long) -> Unit,
    onToggleAccount: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onCreateCalendar: () -> Unit,
    onDeleteCalendar: (Long) -> Unit,
    onHideAccountFromDrawer: (accountKey: String) -> Unit,
    onRecolorAccount: (accountKey: String, argb: Int) -> Unit,
    onRecolorCalendar: (calendarId: Long, argb: Int) -> Unit,
    palette: PaletteId,
    modifier: Modifier = Modifier,
    onRenameCalendar: (Long, String) -> Unit = { _, _ -> },
    tasksEnabled: Boolean = false,
    localOnly: Boolean = false,
    syncOnly: Boolean = false,
) {
    var showDeleteDialogFor by remember { mutableStateOf<CalendarItem?>(null) }
    var showRenameDialogFor by remember { mutableStateOf<CalendarItem?>(null) }
    var showRecolorCalendarFor by remember { mutableStateOf<CalendarItem?>(null) }
    var showRecolorAccountFor by remember { mutableStateOf<AccountGroup?>(null) }

    val views = CalendarView.entries.filter { it.isAlwaysVisible() || tasksEnabled }
    // LocalOnly hides sync calendars; SyncOnly hides local calendars; Hybrid
    // shows everything. Nothing is deleted, just filtered for the drawer.
    val modeFiltered = when {
        localOnly -> calendars.filter { it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL }
        syncOnly -> calendars.filter { it.accountType != CalendarContract.ACCOUNT_TYPE_LOCAL }
        else -> calendars
    }
    // Drop entire accounts the user has hidden from the drawer. The hide is
    // keyed on "<accountType>:<accountName>", matching accountOverrideKey.
    // Restore happens from Settings.
    val displayed = modeFiltered.filter {
        accountOverrideKey(it.accountType, it.accountName) !in drawerHiddenAccountKeys
    }
    val (visible, hidden) = displayed.partition { it.visible }
    // Stable presentation order: account groups in the order their first
    // calendar appears in the (already-sorted) calendar list.
    val groups = visible.groupBy { it.accountName.ifBlank { "" } }
        .entries
        .map { (account, items) -> AccountGroup(account, items) }

    ModalDrawerSheet(modifier = modifier) {
        LazyColumn {
            items(items = views, key = { "view-${it.name}" }) { view ->
                NavigationDrawerItem(
                    label = { Text(view.label()) },
                    selected = view == currentView,
                    onClick = { onSelectView(view) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            item("divider-views") {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            item("calendars-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 4.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.drawer_header_calendars),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCreateCalendar) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.cd_add_calendar),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            groups.forEach { group ->
                val collapsed = group.account in collapsedAccounts
                item("account-${group.account}") {
                    AccountHeader(
                        group = group,
                        collapsed = collapsed,
                        avatarOverrideArgb = avatarOverrideFor(
                            accountOverrideKey(group.accountType, group.account),
                        ),
                        onClick = { onToggleAccount(group.account) },
                        onChangeColor = { showRecolorAccountFor = group },
                        onHideFromDrawer = {
                            onHideAccountFromDrawer(
                                accountOverrideKey(group.accountType, group.account),
                            )
                        },
                    )
                }
                if (!collapsed) {
                    items(items = group.calendars, key = { "cal-${it.id}" }) { cal ->
                        val isLocal = cal.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL
                        CalendarRow(
                            calendar = cal,
                            checked = cal.id !in hiddenCalendarIds,
                            onCheckedChange = { onToggle(cal.id) },
                            onDelete = if (isLocal) {
                                { showDeleteDialogFor = cal }
                            } else {
                                null
                            },
                            onRename = if (isLocal) {
                                { showRenameDialogFor = cal }
                            } else {
                                null
                            },
                            onChangeColor = { showRecolorCalendarFor = cal },
                        )
                    }
                }
            }

            if (hidden.isNotEmpty()) {
                item("hidden-header") {
                    Text(
                        text = stringResource(R.string.drawer_hidden_section),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(items = hidden, key = { "cal-hidden-${it.id}" }) { cal ->
                    CalendarRow(
                        calendar = cal,
                        checked = false,
                        onCheckedChange = null,
                    )
                }
            }

            item("divider-settings") {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            item("settings") {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_settings)) },
                    selected = false,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = onSettingsClick,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        }
    }

    showDeleteDialogFor?.let { cal ->
        DeleteCalendarDialog(
            displayName = cal.displayName,
            onDismiss = { showDeleteDialogFor = null },
            onConfirm = {
                onDeleteCalendar(cal.id)
                showDeleteDialogFor = null
            },
        )
    }

    showRenameDialogFor?.let { cal ->
        RenameCalendarDialog(
            currentName = cal.displayName,
            onDismiss = { showRenameDialogFor = null },
            onConfirm = { newName ->
                onRenameCalendar(cal.id, newName)
                showRenameDialogFor = null
            },
        )
    }

    showRecolorCalendarFor?.let { cal ->
        RecolorDialog(
            title = stringResource(R.string.dialog_recolor_calendar),
            palette = palette,
            currentArgb = cal.displayColor,
            onPick = { argb -> onRecolorCalendar(cal.id, argb) },
            onDismiss = { showRecolorCalendarFor = null },
        )
    }

    showRecolorAccountFor?.let { group ->
        val key = accountOverrideKey(group.accountType, group.account)
        val override = avatarOverrideFor(key)
        val current = override ?: avatarColor(group.accountType, group.account, null).toArgb()
        RecolorDialog(
            title = stringResource(R.string.dialog_recolor_account),
            palette = palette,
            currentArgb = current,
            onPick = { argb -> onRecolorAccount(key, argb) },
            onDismiss = { showRecolorAccountFor = null },
        )
    }
}
