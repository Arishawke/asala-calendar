/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arishawke.asala.calendar.AppViewModel
import com.arishawke.asala.calendar.R
import com.arishawke.asala.calendar.cancelPendingReschedule
import com.arishawke.asala.calendar.closeEditor
import com.arishawke.asala.calendar.closeEventDetail
import com.arishawke.asala.calendar.confirmPendingReschedule
import com.arishawke.asala.calendar.deleteEvent
import com.arishawke.asala.calendar.notifications.OemBatteryAdvisory
import com.arishawke.asala.calendar.openDuplicateEditor
import com.arishawke.asala.calendar.openEditEditor
import com.arishawke.asala.calendar.openEventDetail
import com.arishawke.asala.calendar.ui.calendars.CreateCalendarDialog
import com.arishawke.asala.calendar.ui.eventdetail.EventDetailSheet
import com.arishawke.asala.calendar.ui.eventedit.EventEditScreen
import com.arishawke.asala.calendar.ui.eventedit.RecurringEditScopeDialog
import com.arishawke.asala.calendar.ui.notifications.OemBatteryAdvisoryDialog
import com.arishawke.asala.calendar.ui.search.SearchScreen
import com.arishawke.asala.calendar.ui.settings.SettingsScreen
import com.arishawke.asala.calendar.ui.theme.LocalIs24Hour

// All of the secondary surfaces that AppShell composes on top of the
// main view: settings, search, create-calendar dialog, event detail
// sheet, event editor, and the one-shot OEM battery advisory. Pulled out
// of AppShell so the shell stays under the 200-line threshold and the
// overlays' logic lives in one focused file.
@SuppressLint("InlinedApi") // POST_NOTIFICATIONS is a compile-time constant; safe to inline on pre-33 (launcher no-ops)
@Composable
internal fun AppOverlays(
    vm: AppViewModel,
    showCreateCalendar: Boolean,
    onDismissCreateCalendar: () -> Unit,
    notifPermissionLauncher: ActivityResultLauncher<String>,
) {
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val settingsOpen by vm.settingsOpen.collectAsStateWithLifecycle()
    val notifGranted by vm.notificationPermissionGranted.collectAsStateWithLifecycle()
    val openEvent by vm.detailSheetEvent.collectAsStateWithLifecycle()
    val pendingReschedule by vm.pendingReschedule.collectAsStateWithLifecycle()
    val loadedDetail by vm.loadedDetail.collectAsStateWithLifecycle()
    val editId by vm.editEventId.collectAsStateWithLifecycle()
    val editInstanceMillis by vm.editInstanceMillis.collectAsStateWithLifecycle()
    val searchOpen by vm.searchOpen.collectAsStateWithLifecycle()
    val drawerHiddenAccounts by vm.drawerHiddenAccountsFlow.collectAsStateWithLifecycle()

    BackHandler(enabled = settingsOpen) { vm.closeSettings() }
    if (settingsOpen) {
        SettingsScreen(
            onBack = { vm.closeSettings() },
            drawerHiddenAccounts = drawerHiddenAccounts,
            onRestoreAccountToDrawer = vm::restoreAccountToDrawer,
            notificationPermissionGranted = notifGranted,
            onRequestNotificationPermission = {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
    }

    BackHandler(enabled = searchOpen) { vm.closeSearch() }
    if (searchOpen) {
        SearchScreen(
            hiddenCalendarIdsFlow = vm.hiddenCalendarIdsFlow,
            calendarColorOverridesFlow = vm.calendarColorOverridesFlow,
            eventColorOverridesFlow = vm.eventColorOverridesFlow,
            onBack = { vm.closeSearch() },
            onEventClick = { eid, millis ->
                vm.closeSearch()
                vm.openEventDetail(eid, millis)
            },
        )
    }

    if (showCreateCalendar) {
        CreateCalendarDialog(
            palette = prefs.paletteId,
            onDismiss = onDismissCreateCalendar,
            onConfirm = { name, color ->
                vm.createLocalCalendar(name, color)
                onDismissCreateCalendar()
            },
        )
    }

    openEvent?.let { o ->
        EventDetailSheet(
            detail = loadedDetail,
            instanceMillis = o.instanceMillis,
            notificationPermissionGranted = notifGranted,
            onRequestNotificationPermission = {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = { vm.closeEventDetail() },
            onEdit = { eid ->
                val iMillis = o.instanceMillis
                vm.closeEventDetail()
                vm.openEditEditor(eid, iMillis)
            },
            onDuplicate = { eid ->
                val iMillis = o.instanceMillis
                vm.closeEventDetail()
                vm.openDuplicateEditor(eid, iMillis)
            },
            onDelete = { eid, scope ->
                vm.deleteEvent(
                    eventId = eid,
                    scope = scope,
                    instanceMillis = o.instanceMillis,
                    parentRrule = loadedDetail?.rrule,
                    parentCalendarId = loadedDetail?.calendarId,
                    parentAllDay = loadedDetail?.allDay == true,
                )
            },
        )
    }

    BackHandler(enabled = editId != null) { vm.closeEditor() }
    editId?.let { id ->
        val effectiveId = if (id == -1L) null else id
        // Each open gets its own ViewModelStore so the editor's form state
        // starts fresh; the store is cleared when the editor closes (the
        // composable leaves composition).
        key(id, editInstanceMillis) {
            ScopedViewModelStore {
                EventEditScreen(
                    eventId = effectiveId,
                    instanceMillis = editInstanceMillis,
                    is24Hour = LocalIs24Hour.current,
                    appViewModel = vm,
                    onClose = { vm.closeEditor() },
                    onSaved = { vm.closeEditor() },
                )
            }
        }
    }

    // Drag-reschedule on a recurring event waits here for the user to pick
    // a scope; non-recurring drags save immediately upstream and never
    // produce a pending state.
    pendingReschedule?.let {
        RecurringEditScopeDialog(
            titleRes = R.string.scope_title_edit,
            onPick = { scope -> vm.confirmPendingReschedule(scope) },
            onCancel = { vm.cancelPendingReschedule() },
        )
    }

    var showOemAdvisory by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.oemAdvisoryShown) {
        if (!prefs.oemAdvisoryShown && OemBatteryAdvisory.isAffected()) {
            showOemAdvisory = true
        }
    }
    if (showOemAdvisory) {
        OemBatteryAdvisoryDialog(
            onOpenSettings = {
                context.startActivity(OemBatteryAdvisory.batterySettingsIntent(context))
                vm.markOemAdvisoryShown()
                showOemAdvisory = false
            },
            onSkip = {
                vm.markOemAdvisoryShown()
                showOemAdvisory = false
            },
        )
    }
}
