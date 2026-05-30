/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.calendars.RecolorDialog
import com.arishawke.asala.calendar.ui.theme.PaletteId
import com.arishawke.asala.calendar.ui.theme.Spacing

@Composable
fun EventForm(
    state: EventEditFormState,
    onChange: (EventEditFormState) -> Unit,
    palette: PaletteId,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        OutlinedTextField(
            value = state.title,
            onValueChange = { onChange(state.copy(title = it)) },
            label = { Text(stringResource(R.string.field_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.field_all_day), modifier = Modifier.weight(1f))
            Switch(
                checked = state.allDay,
                onCheckedChange = { onChange(state.withAllDay(it)) },
            )
        }

        DateTimePickerRow(
            label = stringResource(R.string.field_start),
            date = state.startDate,
            time = state.startTime,
            showTime = !state.allDay,
            is24Hour = is24Hour,
            onDateChange = { onChange(state.withStartDate(it)) },
            onTimeChange = { onChange(state.withStartTime(it)) },
        )

        DateTimePickerRow(
            label = stringResource(R.string.field_end),
            date = state.endDate,
            time = state.endTime,
            showTime = !state.allDay,
            is24Hour = is24Hour,
            onDateChange = { onChange(state.withEndDate(it)) },
            onTimeChange = { onChange(state.withEndTime(it)) },
        )

        if (!state.isEndAfterStart) {
            Text(
                stringResource(R.string.error_end_before_start),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        CalendarSelector(
            calendars = state.calendars,
            selectedId = state.selectedCalendarId,
            onSelect = { onChange(state.copy(selectedCalendarId = it)) },
        )

        ColorRow(
            state = state,
            palette = palette,
            onChange = onChange,
        )

        RecurrenceSection(state = state, onChange = onChange)

        ReminderPicker(
            minutesBefore = state.reminderMinutesBefore,
            onChange = { onChange(state.copy(reminderMinutesBefore = it)) },
        )

        OutlinedTextField(
            value = state.location,
            onValueChange = { onChange(state.copy(location = it)) },
            label = { Text(stringResource(R.string.field_location)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = { onChange(state.copy(description = it)) },
            label = { Text(stringResource(R.string.field_description)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Per-event "Color" row. Tap to open the recolor dialog with the
// active palette and a "Reset to calendar color" button. Color is
// app-local (DataStore only), so it's safe to expose on read-only
// synced calendars too.
@Composable
private fun ColorRow(state: EventEditFormState, palette: PaletteId, onChange: (EventEditFormState) -> Unit) {
    var dialogOpen by remember { mutableStateOf(false) }
    val calendar = state.calendars.firstOrNull { it.id == state.selectedCalendarId }
    val calendarColorArgb = calendar?.displayColor ?: 0
    val resolvedArgb = state.colorOverrideArgb ?: calendarColorArgb
    val trailingLabel = if (state.colorOverrideArgb == null) {
        stringResource(R.string.field_color_default)
    } else {
        stringResource(R.string.field_color_custom)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state.selectedCalendarId != null) { dialogOpen = true }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(resolvedArgb)),
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Text(
            text = stringResource(R.string.field_color),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = trailingLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (dialogOpen) {
        RecolorDialog(
            title = stringResource(R.string.field_color),
            palette = palette,
            currentArgb = resolvedArgb,
            onPick = { argb -> onChange(state.copy(colorOverrideArgb = argb)) },
            onDismiss = { dialogOpen = false },
            onReset = { onChange(state.copy(colorOverrideArgb = null)) },
        )
    }
}
