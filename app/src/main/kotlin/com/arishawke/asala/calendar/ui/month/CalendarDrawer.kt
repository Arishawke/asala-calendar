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
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.CalendarItem
import com.arishawke.asala.calendar.ui.calendars.DeleteCalendarDialog
import com.arishawke.asala.calendar.ui.calendars.RecolorDialog
import com.arishawke.asala.calendar.ui.calendars.RenameCalendarDialog
import com.arishawke.asala.calendar.ui.month.drawer.AccountGroup
import com.arishawke.asala.calendar.ui.month.drawer.AccountHeader
import com.arishawke.asala.calendar.ui.month.drawer.CalendarRow
import com.arishawke.asala.calendar.ui.month.drawer.accountOverrideKey
import com.arishawke.asala.calendar.ui.month.drawer.avatarColor
import com.arishawke.asala.calendar.ui.theme.PaletteId

@Suppress("LongParameterList", "LongMethod")
@Composable
fun CalendarDrawerContent(
    calendars: List<CalendarItem>,
    hiddenCalendarIds: Set<Long>,
    drawerHiddenAccountKeys: Set<String>,
    collapsedAccounts: Set<String>,
    // lookup fn not a map: keeps the @Composable param list stable (Map trips ComposeUnstableCollections)
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
    localOnly: Boolean = false,
    syncOnly: Boolean = false,
    // lookup fn not a Set: keeps the @Composable param list stable (a Set<Long>
    // param trips ComposeUnstableCollections), same trick as avatarOverrideFor.
    isOccasionCalendar: (Long) -> Boolean = { false },
) {
    var showDeleteDialogFor by remember { mutableStateOf<CalendarItem?>(null) }
    var showRenameDialogFor by remember { mutableStateOf<CalendarItem?>(null) }
    var showRecolorCalendarFor by remember { mutableStateOf<CalendarItem?>(null) }
    var showRecolorAccountFor by remember { mutableStateOf<AccountGroup?>(null) }

    val modeFiltered = modeFilteredCalendars(calendars, localOnly, syncOnly, isOccasionCalendar)
    // drop accounts hidden from the drawer (key matches accountOverrideKey); restore from Settings
    val displayed = modeFiltered.filter {
        accountOverrideKey(it.accountType, it.accountName) !in drawerHiddenAccountKeys
    }
    val (visible, hidden) = displayed.partition { it.visible }
    // stable order: groups follow first appearance in the already-sorted list
    val groups = visible.groupBy { it.accountName.ifBlank { "" } }
        .entries
        .map { (account, items) -> AccountGroup(account, items) }

    ModalDrawerSheet(modifier = modifier) {
        LazyColumn {
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
                        // the provisioned occasion calendars are feature-owned: the
                        // Settings toggle owns their lifecycle (a drawer delete is
                        // healed back by the next sync), and a rename into the other
                        // kind's keyword would mislabel every event, so neither is
                        // offered. recolor stays available.
                        val userManaged = cal.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL &&
                            !isOccasionCalendar(cal.id)
                        CalendarRow(
                            calendar = cal,
                            checked = cal.id !in hiddenCalendarIds,
                            onCheckedChange = { onToggle(cal.id) },
                            onDelete = if (userManaged) {
                                { showDeleteDialogFor = cal }
                            } else {
                                null
                            },
                            onRename = if (userManaged) {
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

// storage mode filters the drawer only, nothing is deleted. the provisioned
// occasion calendars are feature-owned, so SyncOnly keeps them listed.
private fun modeFilteredCalendars(
    calendars: List<CalendarItem>,
    localOnly: Boolean,
    syncOnly: Boolean,
    isOccasionCalendar: (Long) -> Boolean,
): List<CalendarItem> = when {
    localOnly -> calendars.filter { it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL }
    syncOnly -> calendars.filter {
        it.accountType != CalendarContract.ACCOUNT_TYPE_LOCAL || isOccasionCalendar(it.id)
    }
    else -> calendars
}
