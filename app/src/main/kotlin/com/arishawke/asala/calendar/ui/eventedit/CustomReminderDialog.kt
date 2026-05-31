/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.theme.Spacing

internal enum class CustomReminderUnit(val labelRes: Int, val minutesPer: Int) {
    Minutes(R.string.custom_reminder_unit_minutes, 1),
    Hours(R.string.custom_reminder_unit_hours, 60),
    Days(R.string.custom_reminder_unit_days, 24 * 60),
}

// caps value so value * minutesPer stays well under Int.MAX_VALUE at Days.
private const val MaxValue = 999
private const val MaxValueDigits = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomReminderDialog(initialMinutes: Int?, onDismiss: () -> Unit, onConfirm: (minutes: Int) -> Unit) {
    val (initialUnit, initialValueStr) = remember(initialMinutes) { seedFromMinutes(initialMinutes) }
    var unit by remember { mutableStateOf(initialUnit) }
    var valueText by remember { mutableStateOf(initialValueStr) }
    val parsedValue = valueText.toIntOrNull()
    val isValid = parsedValue != null && parsedValue > 0 && parsedValue <= MaxValue

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_reminder_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { input ->
                        // digits-only, capped, to avoid overflow downstream
                        val digits = input.filter { it.isDigit() }.take(MaxValueDigits)
                        valueText = digits
                    },
                    label = { Text(stringResource(R.string.custom_reminder_value_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = valueText.isNotEmpty() && !isValid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CustomReminderUnit.entries.forEachIndexed { index, choice ->
                        SegmentedButton(
                            selected = unit == choice,
                            onClick = { unit = choice },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = CustomReminderUnit.entries.size,
                            ),
                        ) { Text(stringResource(choice.labelRes)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    parsedValue?.let { onConfirm(it * unit.minutesPer) }
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// seed with the largest unit that divides evenly so 60 shows "1 Hour".
private fun seedFromMinutes(minutes: Int?): Pair<CustomReminderUnit, String> {
    val m = minutes ?: 0
    if (m <= 0) return CustomReminderUnit.Minutes to ""
    return when {
        m % CustomReminderUnit.Days.minutesPer == 0 ->
            CustomReminderUnit.Days to (m / CustomReminderUnit.Days.minutesPer).toString()
        m % CustomReminderUnit.Hours.minutesPer == 0 ->
            CustomReminderUnit.Hours to (m / CustomReminderUnit.Hours.minutesPer).toString()
        else -> CustomReminderUnit.Minutes to m.toString()
    }
}
