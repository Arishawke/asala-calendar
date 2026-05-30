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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.CalendarItem
import com.arishawke.asala.calendar.ui.theme.AsalaCalendarTheme

@Composable
internal fun CalendarRow(
    calendar: CalendarItem,
    checked: Boolean,
    onCheckedChange: (() -> Unit)?,
    onDelete: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onChangeColor: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasMenu = onDelete != null || onRename != null || onChangeColor != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier.clickable(onClick = onCheckedChange)
                } else {
                    Modifier
                },
            )
            .padding(start = 60.dp, end = if (hasMenu) 0.dp else 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(calendar.displayColor)),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = calendar.displayName.ifBlank { calendar.accountName },
            style = MaterialTheme.typography.bodyMedium,
            color = if (onCheckedChange != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        if (onCheckedChange != null) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onCheckedChange() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(calendar.displayColor),
                ),
            )
        }
        if (hasMenu) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.cd_calendar_menu),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (onRename != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename_calendar)) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                    }
                    if (onChangeColor != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_change_color)) },
                            onClick = {
                                menuExpanded = false
                                onChangeColor()
                            },
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete_calendar)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "CalendarRow, light", widthDp = 320)
@Preview(name = "CalendarRow, dark", widthDp = 320, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CalendarRowPreview() {
    AsalaCalendarTheme(dynamicColor = false) {
        CalendarRow(
            calendar = CalendarItem(
                id = 1L,
                displayName = "Personal",
                accountName = "you@example.com",
                accountType = "com.google",
                color = 0xFF1A73E8.toInt(),
                visible = true,
                accessLevel = 700,
            ),
            checked = true,
            onCheckedChange = {},
        )
    }
}
