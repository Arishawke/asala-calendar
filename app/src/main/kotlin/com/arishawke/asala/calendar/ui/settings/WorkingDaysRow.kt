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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import java.time.DayOfWeek
import java.time.format.TextStyle

// Compose's ComposeUnstableCollections lint flags Set<DayOfWeek> as
// non-inferable-stable. The Settings screen recomposes per user action,
// not per frame, so the theoretical instability doesn't matter here;
// suppress rather than reach for kotlinx-collections-immutable.
@Suppress("ComposeUnstableCollections")
@Composable
internal fun WorkingDaysRow(
    workingDays: Set<DayOfWeek>,
    firstDayOfWeek: DayOfWeek?,
    onChange: (Set<DayOfWeek>) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales.get(0)
    val firstDay = firstDayOfWeek ?: firstDayOfWeekFromLocale()
    val orderedDays = remember(firstDay) { orderedWeek(firstDay) }
    val label = stringResource(R.string.settings_working_days_picker)
    val value = remember(workingDays, locale, orderedDays) {
        if (workingDays.isEmpty()) {
            ""
        } else {
            orderedDays
                .filter { it in workingDays }
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { dialogOpen = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value.ifBlank { stringResource(R.string.settings_working_days_empty) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (dialogOpen) {
        WorkingDaysDialog(
            initial = workingDays,
            orderedDays = orderedDays,
            onDismiss = { dialogOpen = false },
            onConfirm = { picked ->
                dialogOpen = false
                onChange(picked)
            },
        )
    }
}

@Suppress("ComposeUnstableCollections")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkingDaysDialog(
    initial: Set<DayOfWeek>,
    orderedDays: List<DayOfWeek>,
    onDismiss: () -> Unit,
    onConfirm: (Set<DayOfWeek>) -> Unit,
) {
    var picked by remember { mutableStateOf(initial) }
    val locale = LocalConfiguration.current.locales.get(0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_working_days_dialog_title)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                orderedDays.forEach { day ->
                    val selected = day in picked
                    FilterChip(
                        selected = selected,
                        onClick = {
                            picked = if (selected) picked - day else picked + day
                        },
                        label = {
                            Text(day.getDisplayName(TextStyle.SHORT, locale))
                        },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked.isNotEmpty(),
                onClick = { onConfirm(picked) },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun orderedWeek(firstDay: DayOfWeek): List<DayOfWeek> {
    val all = DayOfWeek.entries.toList()
    val start = all.indexOf(firstDay)
    return all.drop(start) + all.take(start)
}
