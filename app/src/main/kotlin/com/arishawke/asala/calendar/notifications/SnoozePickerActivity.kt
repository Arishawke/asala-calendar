/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arishawke.asala.calendar.AsalaCalendarApplication
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.settings.UserPreferences
import com.arishawke.asala.calendar.ui.settings.settingsDataStore
import com.arishawke.asala.calendar.ui.theme.AsalaCalendarTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SnoozePickerActivity : ComponentActivity() {

    private val vm: SnoozePickerViewModel by viewModels {
        SnoozePickerViewModel.Factory(application as AsalaCalendarApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alertId = intent.getLongExtra(ReminderConstants.EXTRA_ALERT_ID, -1L)
        val eventId = intent.getLongExtra(ReminderConstants.EXTRA_EVENT_ID, -1L)
        val instanceMillis = intent.getLongExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, -1L)

        setContent {
            AsalaCalendarTheme {
                val defaultMinutes by vm.defaultMinutes.collectAsState()
                SnoozePickerDialog(
                    initialMinutes = defaultMinutes,
                    onPicked = { chosen ->
                        sendBackToReceiver(alertId, eventId, instanceMillis, chosen)
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    private fun sendBackToReceiver(alertId: Long, eventId: Long, instanceMillis: Long, minutes: Int) {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ReminderConstants.ACTION_SNOOZE
            putExtra(ReminderConstants.EXTRA_ALERT_ID, alertId)
            putExtra(ReminderConstants.EXTRA_EVENT_ID, eventId)
            putExtra(ReminderConstants.EXTRA_INSTANCE_MILLIS, instanceMillis)
            putExtra(ReminderConstants.EXTRA_SNOOZE_MINUTES, minutes)
        }
        sendBroadcast(intent)
    }
}

// sentinel for the Custom row; minutes come from the text input
private const val CUSTOM_SENTINEL = -1

@Composable
private fun SnoozePickerDialog(initialMinutes: Int, onPicked: (Int) -> Unit, onDismiss: () -> Unit) {
    var selectedMinutes by remember(initialMinutes) { mutableIntStateOf(initialMinutes) }
    var customText by remember { mutableStateOf("90") }
    val isCustom = selectedMinutes == CUSTOM_SENTINEL
    val customMinutes = customText.toIntOrNull()
    val canConfirm = if (isCustom) customMinutes != null && customMinutes > 0 else true

    val options = listOf(
        5 to R.string.snooze_5min,
        10 to R.string.snooze_10min,
        15 to R.string.snooze_15min,
        30 to R.string.snooze_30min,
        60 to R.string.snooze_60min,
        CUSTOM_SENTINEL to R.string.snooze_custom,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.snooze_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                options.forEach { (minutes, labelRes) ->
                    val selected = selectedMinutes == minutes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                onClick = { selectedMinutes = minutes },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            text = stringResource(labelRes),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                if (isCustom) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { input ->
                            // digits only, capped at 4
                            customText = input.filter { it.isDigit() }.take(4)
                        },
                        label = { Text(stringResource(R.string.snooze_custom_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = customMinutes == null || customMinutes <= 0,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val chosen = if (isCustom) customMinutes ?: return@TextButton else selectedMinutes
                    onPicked(chosen)
                },
                enabled = canConfirm,
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Holds the default snooze minutes from DataStore so the picker can highlight the right row. */
internal class SnoozePickerViewModel(app: AsalaCalendarApplication) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app.settingsDataStore)
    private val _defaultMinutes = MutableStateFlow(10)
    val defaultMinutes: StateFlow<Int> = _defaultMinutes

    init {
        viewModelScope.launch {
            _defaultMinutes.value = prefs.prefs.first().defaultSnoozeMinutes
        }
    }

    class Factory(private val app: AsalaCalendarApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            SnoozePickerViewModel(app) as T
    }
}
