/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.CalendarView
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.StorageMode
import com.arishawke.asala.calendar.isAlwaysVisible
import com.arishawke.asala.calendar.label
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeRow(current: ThemeMode, onChange: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = stringResource(themeModeLabel(current)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_theme)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(themeModeLabel(mode))) },
                    onClick = {
                        onChange(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun themeModeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
    ThemeMode.Amoled -> R.string.theme_amoled
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeFormatRow(current: Boolean?, onChange: (Boolean?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(timeFormatLabel(current))
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_time_format)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf<Boolean?>(null, false, true).forEach { value ->
                DropdownMenuItem(
                    text = { Text(stringResource(timeFormatLabel(value))) },
                    onClick = {
                        onChange(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun timeFormatLabel(value: Boolean?): Int = when (value) {
    null -> R.string.settings_time_format_system
    false -> R.string.settings_time_format_12h
    true -> R.string.settings_time_format_24h
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthScrollStyleRow(current: MonthScrollStyle, onChange: (MonthScrollStyle) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(monthScrollStyleLabel(current))
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_month_scroll_style)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MonthScrollStyle.entries.forEach { style ->
                DropdownMenuItem(
                    text = { Text(stringResource(monthScrollStyleLabel(style))) },
                    onClick = {
                        onChange(style)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun monthScrollStyleLabel(style: MonthScrollStyle): Int = when (style) {
    MonthScrollStyle.Paged -> R.string.settings_month_scroll_style_paged
    MonthScrollStyle.Continuous -> R.string.settings_month_scroll_style_continuous
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeekStartsOnRow(current: DayOfWeek?, onChange: (DayOfWeek?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (current) {
        null -> stringResource(R.string.week_starts_system)
        DayOfWeek.SUNDAY -> stringResource(R.string.week_starts_sunday)
        DayOfWeek.MONDAY -> stringResource(R.string.week_starts_monday)
        DayOfWeek.SATURDAY -> stringResource(R.string.week_starts_saturday)
        else -> current.name
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_week_starts_on)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.week_starts_system)) },
                onClick = {
                    onChange(null)
                    expanded = false
                },
            )
            listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY).forEach { d ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (d) {
                                    DayOfWeek.SUNDAY -> R.string.week_starts_sunday
                                    DayOfWeek.MONDAY -> R.string.week_starts_monday
                                    DayOfWeek.SATURDAY -> R.string.week_starts_saturday
                                    else -> R.string.week_starts_system
                                },
                            ),
                        )
                    },
                    onClick = {
                        onChange(d)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefaultViewRow(current: CalendarView, tasksEnabled: Boolean, onChange: (CalendarView) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val views = CalendarView.entries.filter { it.isAlwaysVisible() || tasksEnabled }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = current.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_default_view)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            views.forEach { v ->
                DropdownMenuItem(
                    text = { Text(v.label()) },
                    onClick = {
                        onChange(v)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageModeRow(current: StorageMode, onChange: (StorageMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Unset shouldn't reach the user once onboarding ran; render it as the
    // local-only label as a safe fallback so the field is never blank.
    val displayMode = if (current == StorageMode.Unset) StorageMode.LocalOnly else current
    val pickable = listOf(StorageMode.LocalOnly, StorageMode.SyncOnly, StorageMode.Hybrid)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = stringResource(storageModeLabel(displayMode)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_storage_mode)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            pickable.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(storageModeLabel(mode))) },
                    onClick = {
                        onChange(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun storageModeLabel(mode: StorageMode): Int = when (mode) {
    StorageMode.LocalOnly -> R.string.storage_mode_local_only
    StorageMode.SyncOnly -> R.string.storage_mode_sync_only
    StorageMode.Hybrid -> R.string.storage_mode_hybrid
    StorageMode.Unset -> R.string.storage_mode_local_only
}
