/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.TimeUnits
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun WorkingHoursRangeRow(
    startHour: Int,
    endHour: Int,
    is24Hour: Boolean,
    onChange: (startHour: Int, endHour: Int) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val label = stringResource(R.string.settings_working_hours_range)
    val locale = LocalLocale.current.platformLocale
    val value =
        "${formatHourOfDay(startHour, is24Hour, locale)} - ${formatHourOfDay(endHour, is24Hour, locale)}"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { dialogOpen = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (dialogOpen) {
        WorkingHoursRangeDialog(
            initialStart = startHour,
            initialEnd = endHour,
            is24Hour = is24Hour,
            onDismiss = { dialogOpen = false },
            onConfirm = { newStart, newEnd ->
                dialogOpen = false
                onChange(newStart, newEnd)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkingHoursRangeDialog(
    initialStart: Int,
    initialEnd: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (startHour: Int, endHour: Int) -> Unit,
) {
    var start by remember { mutableIntStateOf(initialStart) }
    var end by remember { mutableIntStateOf(initialEnd) }
    val isValid = end > start
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_working_hours_range)) },
        text = {
            Column {
                HourDropdown(
                    label = stringResource(R.string.settings_working_hours_start),
                    selected = start,
                    range = 0..(TimeUnits.MaxStartHour - 1),
                    is24Hour = is24Hour,
                    onSelect = { newStart ->
                        start = newStart
                        // keep end > start so the dim math never has an
                        // empty work block; min 1-hour span.
                        if (end <= newStart) end = (newStart + 1).coerceAtMost(TimeUnits.HoursPerDay)
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                HourDropdown(
                    label = stringResource(R.string.settings_working_hours_end),
                    selected = end,
                    range = 1..TimeUnits.HoursPerDay,
                    is24Hour = is24Hour,
                    onSelect = { end = it.coerceAtLeast(start + 1) },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = { onConfirm(start, end) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HourDropdown(label: String, selected: Int, range: IntRange, is24Hour: Boolean, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = remember(range) { range.toList() }
    val locale = LocalLocale.current.platformLocale
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = formatHourOfDay(selected, is24Hour, locale),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(verticalArrangement = Arrangement.Top) {
                options.forEach { hour ->
                    DropdownMenuItem(
                        text = { Text(formatHourOfDay(hour, is24Hour, locale)) },
                        onClick = {
                            onSelect(hour)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

// 24-hour formats 0..24 directly so the end-of-day sentinel reads "24:00".
// 12-hour goes through a locale formatter for correct AM/PM; hour=24
// collapses to 0 since LocalTime.of(24, 0) is invalid.
private fun formatHourOfDay(hour: Int, is24Hour: Boolean, locale: Locale): String {
    if (is24Hour) return String.format(locale, "%02d:00", hour)
    val time = LocalTime.of(hour % TimeUnits.HoursPerDay, 0)
    return time.format(DateTimeFormatter.ofPattern("h a", locale))
}
