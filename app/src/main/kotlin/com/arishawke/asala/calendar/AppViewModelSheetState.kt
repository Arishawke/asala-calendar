/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar

import androidx.lifecycle.viewModelScope
import com.arishawke.asala.calendar.data.EventDetail
import com.arishawke.asala.calendar.data.EventDraft
import com.arishawke.asala.calendar.data.RecurringEditScope
import com.arishawke.asala.calendar.data.shouldClearEventOverrideOnDelete
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Detail-sheet + editor lifecycle for AppViewModel. State backers live on
// AppViewModel itself (MutableStateFlow needs a backing field) but the
// mutators are extracted here so AppViewModel.kt stays focused on the
// view-state orchestration.

fun AppViewModel.openEventDetail(eventId: Long, instanceMillis: Long) {
    detailSheetEventBacker.update { OpenEvent(eventId, instanceMillis) }
    loadedDetailRawBacker.update { null }
    viewModelScope.launch {
        // Store the raw detail; color override resolution happens live in
        // AppViewModel.loadedDetail's combine block, so the sheet keeps up
        // when an override map changes while the sheet is open (e.g., a
        // sync push from another app updating a calendar color).
        loadedDetailRawBacker.update { eventRepository.fetchEventDetail(eventId) }
    }
}

fun AppViewModel.closeEventDetail() {
    detailSheetEventBacker.update { null }
    loadedDetailRawBacker.update { null }
}

fun AppViewModel.openCreateEditor() {
    // Snapshot the currently-viewed date so the editor opens with the
    // user's contextual date pre-filled instead of always defaulting to
    // today. Cleared by openEditEditor / closeEditor.
    editInitialStartDateBacker.update { viewedDate.value }
    editInstanceMillisBacker.update { null }
    editEventIdBacker.update { -1L }
}

fun AppViewModel.openEditEditor(eventId: Long, instanceMillis: Long? = null) {
    editInitialStartDateBacker.update { null }
    editInstanceMillisBacker.update { instanceMillis }
    editEventIdBacker.update { eventId }
}

fun AppViewModel.closeEditor() {
    editEventIdBacker.update { null }
    editInstanceMillisBacker.update { null }
    editInitialStartDateBacker.update { null }
}

fun AppViewModel.deleteEvent(
    eventId: Long,
    scope: RecurringEditScope = RecurringEditScope.AllEvents,
    instanceMillis: Long? = null,
    parentRrule: String? = null,
    parentCalendarId: Long? = null,
    parentAllDay: Boolean = false,
) {
    viewModelScope.launch {
        eventRepository.deleteEvent(
            eventId = eventId,
            scope = scope,
            instanceMillis = instanceMillis,
            parentRrule = parentRrule,
            parentCalendarId = parentCalendarId,
            parentAllDay = parentAllDay,
        )
        // AllEvents removes the event row entirely; any per-event color
        // override is now orphaned and would silently re-attach if the
        // CalendarProvider ever recycled the id. Other scopes preserve
        // the original eventId (this-instance writes an exception; this-
        // and-following truncates the series), so the override is still
        // relevant.
        if (shouldClearEventOverrideOnDelete(scope)) {
            setEventColorOverride(eventId, null)
        }
        closeEventDetail()
    }
}

// Drag-reschedule entry point. Fetches the event detail, preserves
// duration, and routes to either an immediate save (non-recurring) or
// the scope-picker dialog (recurring). All-day events are not supported
// yet; they are silently ignored at this layer.
fun AppViewModel.rescheduleEvent(eventId: Long, instanceMillis: Long, newStartMillis: Long) {
    viewModelScope.launch {
        val detail = eventRepository.fetchEventDetail(eventId) ?: return@launch
        if (detail.allDay) return@launch
        val duration = detail.endMillis - detail.startMillis
        val newEnd = newStartMillis + duration
        if (newStartMillis == detail.startMillis) return@launch
        if (detail.rrule == null) {
            saveRescheduleNow(
                detail = detail,
                instanceMillis = instanceMillis,
                newStart = newStartMillis,
                newEnd = newEnd,
                scope = RecurringEditScope.AllEvents,
            )
        } else {
            pendingRescheduleBacker.update {
                PendingReschedule(
                    detail = detail,
                    instanceMillis = instanceMillis,
                    newStartMillis = newStartMillis,
                    newEndMillis = newEnd,
                )
            }
        }
    }
}

fun AppViewModel.confirmPendingReschedule(scope: RecurringEditScope) {
    val pending = pendingRescheduleBacker.value ?: return
    viewModelScope.launch {
        saveRescheduleNow(
            detail = pending.detail,
            instanceMillis = pending.instanceMillis,
            newStart = pending.newStartMillis,
            newEnd = pending.newEndMillis,
            scope = scope,
        )
        pendingRescheduleBacker.update { null }
    }
}

fun AppViewModel.cancelPendingReschedule() {
    val pending = pendingRescheduleBacker.value
    pendingRescheduleBacker.update { null }
    // Tell the chip to drop its optimistic drag offset so the visual
    // snaps back to where the event actually still is.
    pending?.let { dragRevertSignalBacker.tryEmit(it.detail.eventId) }
}

private suspend fun AppViewModel.saveRescheduleNow(
    detail: EventDetail,
    instanceMillis: Long,
    newStart: Long,
    newEnd: Long,
    scope: RecurringEditScope,
) {
    // ThisInstance writes a single exception (must not recur). AllEvents and
    // ThisAndFollowing both want the parent's RRULE to continue: AllEvents
    // updates the original series; ThisAndFollowing inserts a new tail that
    // continues from the new start with the same recurrence shape.
    val draftRrule = if (scope == RecurringEditScope.ThisInstance) null else detail.rrule
    val draft = EventDraft(
        calendarId = detail.calendarId,
        title = detail.title,
        description = detail.description,
        location = detail.location,
        startMillis = newStart,
        endMillis = newEnd,
        allDay = detail.allDay,
        eventTimezone = detail.eventTimezone,
        rrule = draftRrule,
        status = detail.status,
        availability = detail.availability,
    )
    eventRepository.updateEvent(
        eventId = detail.eventId,
        draft = draft,
        scope = scope,
        instanceMillis = instanceMillis,
        parentRrule = detail.rrule,
        parentAllDay = detail.allDay,
    )
}
