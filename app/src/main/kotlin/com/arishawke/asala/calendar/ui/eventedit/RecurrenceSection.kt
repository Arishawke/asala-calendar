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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.RecurrenceFrequency
import com.arishawke.asala.calendar.data.utcDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrenceSection(state: EventEditFormState, onChange: (EventEditFormState) -> Unit) {
    var showFreqMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.repeats),
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
private const val IntervalFieldWidthFraction = 0.3f

// "Every N weeks" stepper; the data path (save, parse-back, splits) already
// carries INTERVAL, this is just the missing authoring surface.
@Composable
private fun IntervalSection(state: EventEditFormState, onChange: (EventEditFormState) -> Unit) {
    val freq = state.recurrenceFrequency ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.repeats_every))
        OutlinedTextField(
            value = state.recurrenceInterval.toString(),
            onValueChange = { v ->
                val n = v.toIntOrNull()
                if (n != null && n in 1..MaxRecurrenceInterval) onChange(state.copy(recurrenceInterval = n))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(IntervalFieldWidthFraction),
        )
        Text(pluralStringResource(intervalUnitPlural(freq), state.recurrenceInterval))
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
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = state.recurrenceUntilDate == null && state.recurrenceCount == null,
                onClick = { onChange(state.copy(recurrenceUntilDate = null, recurrenceCount = null)) },
            )
            Text(stringResource(R.string.ends_never))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = state.recurrenceUntilDate != null,
                onClick = {
                    onChange(state.copy(recurrenceUntilDate = state.endDate.plusMonths(1), recurrenceCount = null))
                },
            )
            Text(stringResource(R.string.ends_on_date))
            if (state.recurrenceUntilDate != null) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(dateFmt.format(state.recurrenceUntilDate)) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = state.recurrenceCount != null,
                onClick = {
                    onChange(state.copy(recurrenceCount = state.recurrenceCount ?: 10, recurrenceUntilDate = null))
                },
            )
            Text(stringResource(R.string.ends_after_count))
            if (state.recurrenceCount != null) {
                OutlinedTextField(
                    value = state.recurrenceCount.toString(),
                    onValueChange = { v ->
                        val n = v.toIntOrNull()
                        if (n != null && n > 0) onChange(state.copy(recurrenceCount = n))
                    },
                    singleLine = true,
                    modifier = Modifier.padding(start = 8.dp).fillMaxWidth(0.3f),
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

@Composable
private fun frequencyLabel(freq: RecurrenceFrequency?): String = when (freq) {
    null -> stringResource(R.string.repeats_does_not)
    RecurrenceFrequency.Daily -> stringResource(R.string.repeats_daily)
    RecurrenceFrequency.Weekly -> stringResource(R.string.repeats_weekly)
    RecurrenceFrequency.Monthly -> stringResource(R.string.repeats_monthly)
    RecurrenceFrequency.Yearly -> stringResource(R.string.repeats_yearly)
}
