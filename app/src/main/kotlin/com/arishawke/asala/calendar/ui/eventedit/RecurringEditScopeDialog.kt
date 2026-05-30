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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.data.RecurringEditScope

@Composable
fun RecurringEditScopeDialog(titleRes: Int, onPick: (RecurringEditScope) -> Unit, onCancel: () -> Unit) {
    var selected by remember { mutableStateOf<RecurringEditScope?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                ScopeOption(RecurringEditScope.ThisInstance, R.string.scope_this_event, selected) { selected = it }
                ScopeOption(RecurringEditScope.ThisAndFollowing, R.string.scope_this_and_following, selected) {
                    selected =
                        it
                }
                ScopeOption(RecurringEditScope.AllEvents, R.string.scope_all_events, selected) { selected = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = { selected?.let(onPick) },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ScopeOption(
    scope: RecurringEditScope,
    labelRes: Int,
    selected: RecurringEditScope?,
    onSelect: (RecurringEditScope) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = scope == selected,
                role = Role.RadioButton,
                onClick = { onSelect(scope) },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = scope == selected, onClick = { onSelect(scope) })
        Spacer(Modifier.width(8.dp))
        Text(stringResource(labelRes))
    }
}
