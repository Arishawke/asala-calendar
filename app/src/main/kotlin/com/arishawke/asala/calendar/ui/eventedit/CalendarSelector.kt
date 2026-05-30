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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.CalendarAccountGroup
import com.arishawke.asala.calendar.data.CalendarItem
import com.arishawke.asala.calendar.data.EventEditCalendarPicker
import com.arishawke.asala.calendar.ui.theme.Spacing

// Google-Calendar-style calendar selector: an account dropdown (only when
// more than one account has calendars) over a side-scrolling row of color-dot
// chips for the shown account. The selectable list is already filtered by
// EventEditCalendarPicker.filter upstream; this is presentation only.
@Composable
fun CalendarSelector(
    calendars: List<CalendarItem>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(calendars) { EventEditCalendarPicker.groupByAccount(calendars) }
    if (groups.isEmpty()) return // editor surfaces the empty-picker case elsewhere

    // The shown account derives from the selected calendar, so switching the
    // account (which selects that account's first calendar) re-syncs without
    // a separate piece of state to keep in lockstep.
    val shownAccount = remember(groups, selectedId) {
        groups.firstOrNull { group -> group.calendars.any { it.id == selectedId } } ?: groups.first()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (groups.size > 1) {
            AccountDropdown(
                groups = groups,
                shownAccount = shownAccount,
                // switching accounts selects the new account's first calendar;
                // re-picking the account that already holds the selection is a no-op
                onAccountSelected = { group ->
                    if (group.calendars.none { it.id == selectedId }) onSelect(group.calendars.first().id)
                },
            )
        }
        Text(
            text = stringResource(R.string.field_calendar),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CalendarChipRow(
            group = shownAccount,
            selectedId = selectedId,
            onSelect = onSelect,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    groups: List<CalendarAccountGroup>,
    shownAccount: CalendarAccountGroup,
    onAccountSelected: (CalendarAccountGroup) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = shownAccount.accountName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_account)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.accountName) },
                    onClick = {
                        onAccountSelected(group)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CalendarChipRow(group: CalendarAccountGroup, selectedId: Long?, onSelect: (Long) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.xs),
    ) {
        items(items = group.calendars, key = { it.id }) { cal ->
            FilterChip(
                selected = cal.id == selectedId,
                onClick = { onSelect(cal.id) },
                label = { Text(cal.displayName) },
                leadingIcon = {
                    // decorative: the name carries the meaning, dot adds color only
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(cal.displayColor)),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}
