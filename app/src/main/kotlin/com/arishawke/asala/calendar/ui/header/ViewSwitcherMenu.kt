/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.header

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.isAlwaysVisible
import com.arishawke.asala.calendar.label

// Top-bar control that switches the calendar view through a dropdown.
// Took view switching over from the navigation drawer, which now holds
// only calendars and Settings. Current view carries a trailing check.
// tasksEnabled gates the Tasks entry, mirroring the drawer/settings list.
@Composable
internal fun ViewSwitcherMenu(
    currentView: CalendarView,
    tasksEnabled: Boolean,
    onSelectView: (CalendarView) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val views = remember(tasksEnabled) {
        CalendarView.entries.filter { it.isAlwaysVisible() || tasksEnabled }
    }
    // Box anchors the dropdown under the icon and keeps a single top-level
    // emitter (compose-lints ComposeMultipleContentEmitters).
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_view_switcher),
                contentDescription = stringResource(R.string.cd_switch_view),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            views.forEach { view ->
                val isCurrent = view == currentView
                DropdownMenuItem(
                    text = { Text(view.label()) },
                    onClick = {
                        onSelectView(view)
                        expanded = false
                    },
                    trailingIcon = if (isCurrent) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics { selected = isCurrent },
                )
            }
        }
    }
}
