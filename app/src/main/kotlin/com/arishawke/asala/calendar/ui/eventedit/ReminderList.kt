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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.theme.Spacing

// soft cap: hides the add row only, never trims rows written by another client.
internal const val MaxReminders = 10

// appended by the add row when the timed default is None.
internal const val FallbackReminderMinutes = 15

internal fun reminderToAppend(defaultTimedReminderMinutes: Int?): Int =
    defaultTimedReminderMinutes ?: FallbackReminderMinutes

internal fun canAddReminder(count: Int): Boolean = count < MaxReminders

// one ReminderPicker per reminder plus a remove button, with an add row beneath.
// picking None on a row removes it (same effect as the remove button).
@Composable
fun ReminderList(
    reminders: List<Int>,
    onChange: (List<Int>) -> Unit,
    defaultTimedReminderMinutes: Int?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        reminders.forEachIndexed { index, minutes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ReminderPicker(
                    minutesBefore = minutes,
                    onChange = { newValue ->
                        val updated = reminders.toMutableList()
                        if (newValue == null) updated.removeAt(index) else updated[index] = newValue
                        onChange(updated)
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    onChange(reminders.toMutableList().also { it.removeAt(index) })
                }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_reminder_remove),
                    )
                }
            }
        }
        if (canAddReminder(reminders.size)) {
            TextButton(onClick = { onChange(reminders + reminderToAppend(defaultTimedReminderMinutes)) }) {
                Text(stringResource(R.string.reminder_add))
            }
        }
    }
}
