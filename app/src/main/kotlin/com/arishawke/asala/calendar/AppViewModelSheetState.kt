/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

@file:Suppress("TooManyFunctions")

package com.arishawke.asala.calendar

import androidx.lifecycle.viewModelScope
import com.arishawke.asala.calendar.data.EventDetail
import com.arishawke.asala.calendar.data.EventDraft
import com.arishawke.asala.calendar.data.RecurringEditScope
import com.arishawke.asala.calendar.data.shouldClearEventOverrideOnDelete
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// detail-sheet + editor mutators for AppViewModel, extracted here so the
// view-state orchestration stays in AppViewModel.kt. backers live there
// because MutableStateFlow needs a backing field.

fun AppViewModel.openEventDetail(eventId: Long, instanceMillis: Long) {
    detailSheetEventBacker.update { OpenEvent(eventId, instanceMillis) }
    loadedDetailRawBacker.update { null }
    viewModelScope.launch {
        // store raw only; override resolution happens live in loadedDetail
        loadedDetailRawBacker.update { eventRepository.fetchEventDetail(eventId) }
    }
}

fun AppViewModel.closeEventDetail() {
    detailSheetEventBacker.update { null }
    loadedDetailRawBacker.update { null }
}

fun AppViewModel.openCreateEditor() {
    // snapshot viewedDate so the editor pre-fills the contextual date, not today
    editInitialStartDateBacker.update { viewedDate.value }
    editInstanceMillisBacker.update { null }
    editDuplicateSourceIdBacker.update { null }
    editEventIdBacker.update { -1L }
}

fun AppViewModel.openEditEditor(eventId: Long, instanceMillis: Long? = null) {
    editInitialStartDateBacker.update { null }
    editInstanceMillisBacker.update { instanceMillis }
    editDuplicateSourceIdBacker.update { null }
    editEventIdBacker.update { eventId }
}

// create mode (-1L) seeded from an existing event for Duplicate.
// instanceMillis is the opened occurrence so a recurring dup lands on it.
fun AppViewModel.openDuplicateEditor(eventId: Long, instanceMillis: Long? = null) {
    editInitialStartDateBacker.update { null }
    editInstanceMillisBacker.update { instanceMillis }
    editDuplicateSourceIdBacker.update { eventId }
    editEventIdBacker.update { -1L }
}

fun AppViewModel.closeEditor() {
    editEventIdBacker.update { null }
    editInstanceMillisBacker.update { null }
    editInitialStartDateBacker.update { null }
    editDuplicateSourceIdBacker.update { null }
}

fun AppViewModel.deleteEvent(
    eventId: Long,
    scope: RecurringEditScope = RecurringEditScope.AllEvents,
    instanceMillis: Long? = null,
    parentRrule: String? = null,
    parentAllDay: Boolean = false,
) {
    viewModelScope.launch {
        eventRepository.deleteEvent(
            eventId = eventId,
            scope = scope,
            instanceMillis = instanceMillis,
            parentRrule = parentRrule,
            parentAllDay = parentAllDay,
        )
        // AllEvents drops the row, orphaning the per-event override (would
        // re-attach on a recycled id); other scopes keep the eventId.
        if (shouldClearEventOverrideOnDelete(scope)) {
            setEventColorOverride(eventId, null)
        }
        closeEventDetail()
    }
}

// drag-reschedule entry: preserves duration, routes to immediate save
// (non-recurring) or the scope picker (recurring). all-day ignored for now.
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
    // tell the chip to drop its optimistic offset and snap back
    pending?.let { dragRevertSignalBacker.tryEmit(it.detail.eventId) }
}

private suspend fun AppViewModel.saveRescheduleNow(
    detail: EventDetail,
    instanceMillis: Long,
    newStart: Long,
    newEnd: Long,
    scope: RecurringEditScope,
) {
    // ThisInstance writes a one-off exception (no rrule); the other scopes
    // keep the parent rrule so the series continues
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
