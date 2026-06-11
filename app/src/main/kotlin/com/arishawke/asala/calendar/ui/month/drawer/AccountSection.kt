/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.month.drawer

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.CalendarItem
import com.arishawke.asala.calendar.ui.theme.AsalaCalendarTheme
import com.arishawke.asala.calendar.ui.theme.OkabeItoPalette
import com.arishawke.asala.calendar.ui.theme.WcagContrast

internal data class AccountGroup(val account: String, val calendars: List<CalendarItem>) {
    val accountType: String get() = calendars.firstOrNull()?.accountType.orEmpty()
}

@Composable
internal fun AccountHeader(
    group: AccountGroup,
    collapsed: Boolean,
    avatarOverrideArgb: Int?,
    onClick: () -> Unit,
    onChangeColor: () -> Unit,
    onHideFromDrawer: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val collapseLabel = stringResource(
        if (collapsed) R.string.cd_expand_account else R.string.cd_collapse_account,
        group.account.ifBlank { stringResource(R.string.account_type_local) },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(
            accountType = group.accountType,
            account = group.account,
            overrideArgb = avatarOverrideArgb,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.account.ifBlank { stringResource(R.string.account_type_local) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val typeLabel = accountTypeLabel(group.accountType)
            if (typeLabel != null) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = collapseLabel,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.cd_account_menu),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_change_account_color)) },
                    onClick = {
                        menuExpanded = false
                        onChangeColor()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_hide_from_drawer)) },
                    onClick = {
                        menuExpanded = false
                        onHideFromDrawer()
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountAvatar(accountType: String, account: String, overrideArgb: Int?) {
    val initial = account.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    val bg = avatarColor(accountType, account, overrideArgb)
    // foreground picked by actual WCAG contrast against the swatch fill.
    val fg = Color(WcagContrast.onColor(bg.toArgb()))
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// deterministic avatar color; hash on "$type:$account" so same-name accounts on
// different types don't collide. override wins.
internal fun avatarColor(accountType: String, account: String, overrideArgb: Int?): Color {
    if (overrideArgb != null) return Color(overrideArgb)
    if (account.isBlank()) return OkabeItoPalette[0]
    val key = "$accountType:$account"
    val idx = (key.hashCode().toUInt() % OkabeItoPalette.size.toUInt()).toInt()
    return OkabeItoPalette[idx]
}

internal fun accountOverrideKey(accountType: String, account: String): String = "$accountType:$account"

@Composable
private fun accountTypeLabel(accountType: String): String? = when (accountType) {
    "com.google" -> stringResource(R.string.account_type_google)
    "LOCAL" -> stringResource(R.string.account_type_local)
    "com.android.exchange" -> stringResource(R.string.account_type_exchange)
    "" -> null
    // DAVx5 syncs over CalDAV; show the protocol, not the raw package id
    else -> if (
        accountType.contains("caldav", ignoreCase = true) ||
        accountType.startsWith("bitfire.at.davdroid")
    ) {
        stringResource(R.string.account_type_caldav)
    } else {
        accountType
    }
}

@Preview(name = "AccountHeader, light", widthDp = 320)
@Preview(name = "AccountHeader, dark", widthDp = 320, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountHeaderPreview() {
    AsalaCalendarTheme(dynamicColor = false) {
        AccountHeader(
            group = AccountGroup(
                account = "you@example.com",
                calendars = listOf(
                    CalendarItem(
                        id = 1L,
                        displayName = "Personal",
                        accountName = "you@example.com",
                        accountType = "com.google",
                        color = 0xFF1A73E8.toInt(),
                        visible = true,
                        accessLevel = 700,
                    ),
                ),
            ),
            collapsed = false,
            avatarOverrideArgb = null,
            onClick = {},
            onChangeColor = {},
            onHideFromDrawer = {},
        )
    }
}
