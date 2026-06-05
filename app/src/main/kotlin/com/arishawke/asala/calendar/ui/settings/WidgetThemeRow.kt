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
import com.arishawke.asala.calendar.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetThemeRow(current: WidgetThemeMode, onChange: (WidgetThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = stringResource(widgetThemeModeLabel(current)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_widget_theme)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WidgetThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(widgetThemeModeLabel(mode))) },
                    onClick = {
                        onChange(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

// FollowApp gets its own label; the explicit modes reuse the app theme strings.
private fun widgetThemeModeLabel(mode: WidgetThemeMode): Int = when (mode) {
    WidgetThemeMode.FollowApp -> R.string.settings_widget_theme_follow_app
    WidgetThemeMode.System -> R.string.theme_system
    WidgetThemeMode.Light -> R.string.theme_light
    WidgetThemeMode.Dark -> R.string.theme_dark
    WidgetThemeMode.Amoled -> R.string.theme_amoled
}
