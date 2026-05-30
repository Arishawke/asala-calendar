/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arishawke.asala.calendar.AppViewModel
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.notifications.ReminderRationaleDialog
import com.arishawke.asala.calendar.ui.theme.Spacing
import kotlinx.coroutines.launch

@SuppressLint("InlinedApi") // POST_NOTIFICATIONS is a compile-time constant; safe to inline on pre-33 (launcher no-ops)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    eventId: Long? = null,
    instanceMillis: Long? = null,
    is24Hour: Boolean = false,
    appViewModel: AppViewModel,
    onClose: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    // Snapshot the storage mode once at editor open. The Factory keys the
    // ViewModel on this value and the picker filter is fixed for the life of
    // the editor; a mid-edit mode flip should not silently rebuild the VM.
    val storageMode = remember { appViewModel.prefs.value.storageMode }
    // Same snapshot pattern for default-duration: pref changes mid-edit must
    // not retroactively shift the open form.
    val defaultDurationMinutes = remember { appViewModel.prefs.value.defaultDurationMinutes }
    val defaultTimedReminderMinutes = remember { appViewModel.prefs.value.defaultTimedReminderMinutes }
    val defaultAllDayReminderMinutes = remember { appViewModel.prefs.value.defaultAllDayReminderMinutes }
    // Existing per-event override hex if this editor is opening an existing
    // event. Null in the create flow (no eventId) or when the event has no
    // override.
    val initialColorOverrideArgb = remember(eventId) {
        eventId?.let { appViewModel.eventColorOverridesFlow.value[it] }
    }
    val palette = remember { appViewModel.prefs.value.paletteId }
    // Snapshot the effective hide set (manual hides + account hides +
    // storage-mode hides) so the picker only shows calendars the user
    // can actually see in the drawer.
    val hiddenCalendarIds = remember { appViewModel.hiddenCalendarIdsFlow.value }
    // Snapshot the date the user was looking at when they tapped FAB so
    // the new-event editor opens on that date instead of today. Null for
    // edit-existing flow; the load path then uses the existing event's
    // own dates.
    val initialStartDate = remember(eventId) {
        if (eventId == null) appViewModel.editInitialStartDate.value else null
    }
    val vm: EventEditViewModel = viewModel(
        factory = EventEditViewModel.Factory(
            appContext = ctx.applicationContext,
            eventId = eventId,
            instanceMillis = instanceMillis,
            storageMode = storageMode,
            defaultDurationMinutes = defaultDurationMinutes,
            defaultTimedReminderMinutes = defaultTimedReminderMinutes,
            defaultAllDayReminderMinutes = defaultAllDayReminderMinutes,
            initialColorOverrideArgb = initialColorOverrideArgb,
            hiddenCalendarIds = hiddenCalendarIds,
            initialStartDate = initialStartDate,
        ),
    )
    val state by vm.form.collectAsStateWithLifecycle()
    val saveError by vm.saveError.collectAsStateWithLifecycle()
    val notifGranted by appViewModel.notificationPermissionGranted.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showScopeDialog by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }

    // Persist the per-event color override post-save once we know the event
    // ID. Writing here instead of inside EventSave keeps color out of the
    // EventDraft / CalendarContract write entirely.
    fun persistColorOverride(savedEventId: Long) {
        appViewModel.setEventColorOverride(savedEventId, state.colorOverrideArgb)
    }

    // Proceed with the actual save (called after rationale is resolved).
    fun doSave() {
        if (eventId != null && vm.isEditingRecurring) {
            showScopeDialog = true
        } else {
            scope.launch {
                val r = vm.save()
                if (r is SaveResult.Success) {
                    persistColorOverride(r.eventId)
                    onSaved(r.eventId)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        appViewModel.refreshNotificationPermission()
        doSave()
    }

    // Show rationale before the system prompt when the user has a reminder
    // set but notification permission has not been granted.
    fun beginSave() {
        if (state.reminderMinutesBefore != null && !notifGranted) {
            showRationale = true
        } else {
            doSave()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (eventId == null) R.string.event_new else R.string.event_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = state.isEndAfterStart && state.selectedCalendarId != null,
                        onClick = { beginSave() },
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (saveError) {
                Text(
                    text = stringResource(R.string.error_save_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
            }
            EventForm(
                state = state,
                onChange = { newState -> vm.updateForm { newState } },
                palette = palette,
                is24Hour = is24Hour,
            )
        }
    }

    if (showRationale) {
        ReminderRationaleDialog(
            onContinue = {
                showRationale = false
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onNotNow = {
                showRationale = false
                doSave()
            },
        )
    }

    if (showScopeDialog) {
        RecurringEditScopeDialog(
            titleRes = R.string.scope_title_edit,
            onPick = { pickedScope ->
                showScopeDialog = false
                scope.launch {
                    val r = vm.save(scope = pickedScope, instanceMillis = instanceMillis)
                    if (r is SaveResult.Success) {
                        persistColorOverride(r.eventId)
                        onSaved(r.eventId)
                    }
                }
            },
            onCancel = { showScopeDialog = false },
        )
    }
}
