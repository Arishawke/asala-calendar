/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.RecurrenceFrequency
import com.arishawke.asala.calendar.data.utcDate
import com.arishawke.asala.calendar.ui.theme.Spacing
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrenceSection(state: EventEditFormState, onChange: (EventEditFormState) -> Unit) {
    var showFreqMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            stringResource(R.string.repeats),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        ExposedDropdownMenuBox(
            expanded = showFreqMenu,
            onExpandedChange = { showFreqMenu = !showFreqMenu },
        ) {
            AssistChip(
                onClick = { showFreqMenu = true },
                label = { Text(frequencyLabel(state.recurrenceFrequency)) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = showFreqMenu,
                onDismissRequest = { showFreqMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.repeats_does_not)) },
                    onClick = {
                        onChange(
                            state.copy(
                                recurrenceFrequency = null,
                                recurrenceInterval = 1,
                                recurrenceUntilDate = null,
                                recurrenceCount = null,
                            ),
                        )
                        showFreqMenu = false
                    },
                )
                RecurrenceFrequency.entries.forEach { freq ->
                    DropdownMenuItem(
                        text = { Text(frequencyLabel(freq)) },
                        onClick = {
                            // switching frequency resets the interval (every 2 weeks
                            // is not every 2 days); re-picking the same one keeps it.
                            val interval = if (freq == state.recurrenceFrequency) state.recurrenceInterval else 1
                            onChange(state.copy(recurrenceFrequency = freq, recurrenceInterval = interval))
                            showFreqMenu = false
                        },
                    )
                }
            }
        }
    }

    if (state.recurrenceFrequency != null) {
        IntervalSection(state = state, onChange = onChange)
        EndConditionSection(state = state, onChange = onChange)
    }
}

private const val MaxRecurrenceInterval = 99
private const val MaxIntervalDigits = 2
private const val MaxRecurrenceCount = 999
private const val MaxCountDigits = 3

// "Every N weeks" stepper; the data path (save, parse-back, splits) already
// carries INTERVAL, this is just the missing authoring surface.
@Composable
private fun IntervalSection(state: EventEditFormState, onChange: (EventEditFormState) -> Unit) {
    val freq = state.recurrenceFrequency ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(stringResource(R.string.repeats_every), style = MaterialTheme.typography.bodyMedium)
        CompactNumberField(
            value = state.recurrenceInterval,
            maxDigits = MaxIntervalDigits,
            range = 1..MaxRecurrenceInterval,
            width = IntervalFieldWidth,
            onCommit = { onChange(state.copy(recurrenceInterval = it)) },
        )
        Text(
            pluralStringResource(intervalUnitPlural(freq), state.recurrenceInterval),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun intervalUnitPlural(freq: RecurrenceFrequency): Int = when (freq) {
    RecurrenceFrequency.Daily -> R.plurals.recurrence_interval_days
    RecurrenceFrequency.Weekly -> R.plurals.recurrence_interval_weeks
    RecurrenceFrequency.Monthly -> R.plurals.recurrence_interval_months
    RecurrenceFrequency.Yearly -> R.plurals.recurrence_interval_years
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndConditionSection(state: EventEditFormState, onChange: (EventEditFormState) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    val locale = LocalLocale.current.platformLocale
    val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        EndConditionRow(
            selected = state.recurrenceUntilDate == null && state.recurrenceCount == null,
            label = stringResource(R.string.ends_never),
            onSelect = { onChange(state.copy(recurrenceUntilDate = null, recurrenceCount = null)) },
        )
        EndConditionRow(
            selected = state.recurrenceUntilDate != null,
            label = stringResource(R.string.ends_on_date),
            onSelect = {
                onChange(state.copy(recurrenceUntilDate = state.endDate.plusMonths(1), recurrenceCount = null))
            },
        ) {
            state.recurrenceUntilDate?.let { until ->
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(dateFmt.format(until)) },
                )
            }
        }
        EndConditionRow(
            selected = state.recurrenceCount != null,
            label = stringResource(R.string.ends_after_count),
            onSelect = {
                onChange(state.copy(recurrenceCount = state.recurrenceCount ?: 10, recurrenceUntilDate = null))
            },
        ) {
            state.recurrenceCount?.let { count ->
                // a synced RRULE may carry COUNT above the authoring cap; widen
                // the bounds to the loaded value so it stays valid and retypeable
                CompactNumberField(
                    value = count,
                    maxDigits = maxOf(MaxCountDigits, count.toString().length),
                    range = 1..maxOf(MaxRecurrenceCount, count),
                    width = CountFieldWidth,
                    onCommit = { onChange(state.copy(recurrenceCount = it)) },
                )
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = (state.recurrenceUntilDate ?: state.endDate.plusMonths(1))
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        // UNTIL before the event start expands to zero occurrences; block those
        // dates so the user can't pick a recurrence that ends before it begins.
        val startMillis = state.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= startMillis
                override fun isSelectableYear(year: Int): Boolean = year >= state.startDate.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = utcDate(millis)
                        onChange(state.copy(recurrenceUntilDate = picked))
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }
}

// radio + label form the selectable unit (Role.RadioButton, radio itself
// decorative); the trailing widget sits OUTSIDE it so a text field or chip
// keeps its own semantics node instead of merging into the row announcement.
@Composable
private fun EndConditionRow(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        trailing?.invoke()
    }
}

@Composable
private fun frequencyLabel(freq: RecurrenceFrequency?): String = when (freq) {
    null -> stringResource(R.string.repeats_does_not)
    RecurrenceFrequency.Daily -> stringResource(R.string.repeats_daily)
    RecurrenceFrequency.Weekly -> stringResource(R.string.repeats_weekly)
    RecurrenceFrequency.Monthly -> stringResource(R.string.repeats_monthly)
    RecurrenceFrequency.Yearly -> stringResource(R.string.repeats_yearly)
}
