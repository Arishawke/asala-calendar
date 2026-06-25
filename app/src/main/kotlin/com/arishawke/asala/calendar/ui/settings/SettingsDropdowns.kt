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
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.theme.PaletteId
import com.arishawke.asala.calendar.ui.theme.Spacing

// closed option sets live next to the pickers they drive.

// default-event-duration, minutes. 0 excluded: a zero-length event fails
// the editor's isEndAfterStart guard and is unsaveable. suppress: the
// values are meaningful as a set, not as individual named constants.
@Suppress("MagicNumber")
private val DefaultDurationOptionsMinutes = listOf(15, 30, 45, 60, 90, 120)

// default-snooze, minutes. mirrors the per-notification snooze picker.
@Suppress("MagicNumber")
private val DefaultSnoozeOptionsMinutes = listOf(5, 10, 15, 30, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaletteRow(current: PaletteId, onChange: (PaletteId) -> Unit) {
    val options = PaletteId.entries
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = stringResource(current.labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_palette_label)) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { palette ->
                DropdownMenuItem(
                    text = { Text(stringResource(palette.labelRes)) },
                    onClick = {
                        onChange(palette)
                        open = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolbarPositionRow(current: ToolbarPosition, onChange: (ToolbarPosition) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = stringResource(toolbarPositionLabel(current)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_toolbar_position)) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ToolbarPosition.entries.forEach { pos ->
                DropdownMenuItem(
                    text = { Text(stringResource(toolbarPositionLabel(pos))) },
                    onClick = {
                        onChange(pos)
                        open = false
                    },
                )
            }
        }
    }
}

private fun toolbarPositionLabel(pos: ToolbarPosition): Int = when (pos) {
    ToolbarPosition.Top -> R.string.settings_toolbar_position_top
    ToolbarPosition.Bottom -> R.string.settings_toolbar_position_bottom
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefaultDurationDropdown(current: Int, onChange: (Int) -> Unit) {
    val options = DefaultDurationOptionsMinutes
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = "$current min",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_default_duration)) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { min ->
                DropdownMenuItem(
                    text = { Text("$min min") },
                    onClick = {
                        onChange(min)
                        open = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefaultSnoozeDropdown(current: Int, onChange: (Int) -> Unit) {
    val options = DefaultSnoozeOptionsMinutes
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = "$current min",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_default_snooze)) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { min ->
                DropdownMenuItem(
                    text = { Text("$min min") },
                    onClick = {
                        onChange(min)
                        open = false
                    },
                )
            }
        }
    }
}
