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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arishawke.asala.calendar.AppViewModel
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.ui.BarScaffold
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
    // snapshot prefs at editor open: a mid-edit pref flip must not rebuild
    // the VM or shift the open form.
    val storageMode = remember { appViewModel.prefs.value.storageMode }
    val defaultDurationMinutes = remember { appViewModel.prefs.value.defaultDurationMinutes }
    val defaultTimedReminderMinutes = remember { appViewModel.prefs.value.defaultTimedReminderMinutes }
    val defaultAllDayReminderMinutes = remember { appViewModel.prefs.value.defaultAllDayReminderMinutes }
    // source event id when opened via Duplicate.
    val duplicateSourceId = remember { appViewModel.editDuplicateSourceId.value }
    // existing override for the edited event, or the source when duplicating,
    // so the copy keeps its color.
    val initialColorOverrideArgb = remember(eventId, duplicateSourceId) {
        (eventId ?: duplicateSourceId)?.let { appViewModel.eventColorOverridesFlow.value[it] }
    }
    val palette = remember { appViewModel.prefs.value.paletteId }
    // effective hide set so the picker only shows drawer-visible calendars.
    val hiddenCalendarIds = remember { appViewModel.hiddenCalendarIdsFlow.value }
    // date the user was viewing at FAB tap so a new event opens there, not
    // today. null for edit-existing (load path uses the event's own dates).
    val initialStartDate = remember(eventId) {
        if (eventId == null) appViewModel.editInitialStartDate.value else null
    }
    // time from a timeline empty-slot tap; null (FAB path) falls back to the
    // next round hour inside forNewEvent.
    val initialStartTime = remember(eventId) {
        if (eventId == null) appViewModel.editInitialStartTime.value else null
    }
    // raw share-sheet text, read once at open; edit-existing (eventId != null)
    // never consumes it, matching initialStartDate/initialStartTime above.
    val shareText = remember(eventId) {
        if (eventId == null) appViewModel.pendingShareText.value else null
    }
    val vm: EventEditViewModel = viewModel(
        factory = EventEditViewModel.Factory(
            appContext = ctx.applicationContext,
            eventId = eventId,
            instanceMillis = instanceMillis,
            duplicateFromEventId = duplicateSourceId,
            storageMode = storageMode,
            defaultDurationMinutes = defaultDurationMinutes,
            defaultTimedReminderMinutes = defaultTimedReminderMinutes,
            defaultAllDayReminderMinutes = defaultAllDayReminderMinutes,
            initialColorOverrideArgb = initialColorOverrideArgb,
            hiddenCalendarIds = hiddenCalendarIds,
            initialStartDate = initialStartDate,
            initialStartTime = initialStartTime,
            shareText = shareText,
        ),
    )
    val state by vm.form.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val saveError by vm.saveError.collectAsStateWithLifecycle()
    val notifGranted by appViewModel.notificationPermissionGranted.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showScopeDialog by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }

    // persist override post-save: keeps color out of EventDraft /
    // CalendarContract entirely.
    fun persistColorOverride(savedEventId: Long) {
        appViewModel.setEventColorOverride(savedEventId, state.colorOverrideArgb)
    }

    // after a successful save, ask the calendar to reveal the saved event in
    // whatever view is showing. reads the freshest form, not the captured state.
    fun revealSaved(savedEventId: Long) {
        val f = vm.form.value
        appViewModel.revealSavedEvent(
            date = f.startDate,
            time = if (f.allDay) null else f.startTime,
            eventId = savedEventId,
        )
    }

    fun doSave() {
        if (eventId != null && vm.isEditingRecurring) {
            showScopeDialog = true
        } else {
            scope.launch {
                val r = vm.save()
                if (r is SaveResult.Success) {
                    persistColorOverride(r.eventId)
                    revealSaved(r.eventId)
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

    // rationale before the system prompt when a reminder is set but
    // notification permission is not yet granted.
    fun beginSave() {
        if (state.reminderMinutes.isNotEmpty() && !notifGranted) {
            showRationale = true
        } else {
            doSave()
        }
    }

    BarScaffold(
        bar = { insets ->
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
                windowInsets = insets,
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
            if (loading) {
                // edit / duplicate fetch their data async; show a spinner until
                // it lands rather than an editable blank form that gets replaced.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                EventForm(
                    state = state,
                    onChange = { newState -> vm.updateForm { newState } },
                    palette = palette,
                    is24Hour = is24Hour,
                    quickAddInitialText = vm.initialQuickAddText,
                )
            }
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
                        revealSaved(r.eventId)
                        onSaved(r.eventId)
                    }
                }
            },
            onCancel = { showScopeDialog = false },
        )
    }
}
