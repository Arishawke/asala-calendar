/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.arishawke.asala.calendar.R

// minutes-before presets. null = no reminder; 0 = at start.
private val ReminderPresets = listOf(
    null,
    0,
    5,
    10,
    15,
    30,
    60,
    24 * 60,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPicker(
    minutesBefore: Int?,
    onChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    labelResId: Int = R.string.field_reminder,
) {
    var expanded by remember { mutableStateOf(false) }
    var customDialogOpen by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = reminderLabel(minutesBefore),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(labelResId)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReminderPresets.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(reminderLabel(choice)) },
                    onClick = {
                        onChange(choice)
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reminder_custom)) },
                onClick = {
                    expanded = false
                    customDialogOpen = true
                },
            )
        }
    }
    if (customDialogOpen) {
        CustomReminderDialog(
            initialMinutes = minutesBefore,
            onDismiss = { customDialogOpen = false },
            onConfirm = { value ->
                customDialogOpen = false
                onChange(value)
            },
        )
    }
}

// snap exact presets, else format with the largest unit that divides
// cleanly (180 -> "3 h before", 2880 -> "2 days before").
@Composable
private fun reminderLabel(m: Int?): String = when (m) {
    null -> stringResource(R.string.reminder_none)
    0 -> stringResource(R.string.reminder_at_time)
    60 -> stringResource(R.string.reminder_one_hour)
    24 * 60 -> stringResource(R.string.reminder_one_day)
    else -> when {
        m % (24 * 60) == 0 -> {
            val days = m / (24 * 60)
            pluralStringResource(R.plurals.reminder_days_before, days, days)
        }
        m % 60 == 0 -> {
            val hours = m / 60
            pluralStringResource(R.plurals.reminder_hours_before, hours, hours)
        }
        else -> stringResource(R.string.reminder_minutes_before, m)
    }
}
