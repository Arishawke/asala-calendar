/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.calendars

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.theme.PaletteId
import com.arishawke.asala.calendar.ui.theme.Spacing

@Composable
internal fun CreateCalendarDialog(
    palette: PaletteId,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    // mid-hue slot so the default isn't an edge color (grey / tomato).
    val seedIndex = palette.swatches.size / 2
    var selectedColorArgb by remember(palette) { mutableIntStateOf(palette.swatches[seedIndex].toArgb()) }
    val canSave = name.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_calendar_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.create_calendar_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.create_calendar_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                Text(
                    text = stringResource(R.string.create_calendar_color_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                ColorSwatchGrid(
                    palette = palette,
                    selectedArgb = selectedColorArgb,
                    onSelect = { selectedColorArgb = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), selectedColorArgb) },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.create_calendar_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
