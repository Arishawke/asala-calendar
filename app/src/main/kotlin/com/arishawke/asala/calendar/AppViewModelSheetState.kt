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
import com.arishawke.asala.calendar.data.rescheduleDraftShape
import com.arishawke.asala.calendar.data.shouldClearEventOverrideOnDelete
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// detail-sheet + editor mutators for AppViewModel, extracted here so the
// view-state orchestration stays in AppViewModel.kt. backers live there
// because MutableStateFlow needs a backing field.

fun AppViewModel.openEventDetail(eventId: Long, instanceMillis: Long) {
    detailSheetEventBacker.update { OpenEvent(eventId, instanceMillis) }
    loadedDetailRawBacker.update { null }
    deleteFailedBacker.update { false }
    viewModelScope.launch {
        // store raw only; override resolution happens live in loadedDetail
        loadedDetailRawBacker.update { eventRepository.fetchEventDetail(eventId) }
    }
}

fun AppViewModel.closeEventDetail() {
    detailSheetEventBacker.update { null }
    loadedDetailRawBacker.update { null }
    deleteFailedBacker.update { false }
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
        deleteFailedBacker.update { false }
        // only finalize when the provider actually deleted. on failure keep the
        // sheet open AND surface an explicit message so the failed destructive op
        // isn't silent, rather than closing on a false success and (AllEvents)
        // dropping a color override that could re-attach to a recycled id.
        val deleted = eventRepository.deleteEvent(
            eventId = eventId,
            scope = scope,
            instanceMillis = instanceMillis,
            parentRrule = parentRrule,
            parentAllDay = parentAllDay,
        )
        if (!deleted) {
            deleteFailedBacker.update { true }
            return@launch
        }
        // AllEvents drops the row, orphaning the per-event override (would
        // re-attach on a recycled id); other scopes keep the eventId.
        if (shouldClearEventOverrideOnDelete(scope)) {
            setEventColorOverride(eventId, null)
        }
        closeEventDetail()
    }
}

// drag-reschedule entry: the pure rescheduleOutcome decides; this keeps the I/O.
// preserves duration, routes to immediate save (non-recurring) or the scope
// picker (recurring). every abandon path reverts the optimistic chip exactly once.
fun AppViewModel.rescheduleEvent(eventId: Long, instanceMillis: Long, newStartMillis: Long) {
    viewModelScope.launch {
        val detail = eventRepository.fetchEventDetail(eventId)
        when (val outcome = rescheduleOutcome(detail, instanceMillis, newStartMillis)) {
            RescheduleDecision.RevertNoDetail,
            RescheduleDecision.RevertAllDay,
            -> dragRevertSignalBacker.tryEmit(eventId)
            RescheduleDecision.NoOp -> Unit
            // requireNotNull documents the invariant: the decision is only ever
            // Save/AskScope when detail loaded (else RevertNoDetail). fail loud
            // rather than silently dropping the write if that ever breaks.
            is RescheduleDecision.Save ->
                saveRescheduleNow(
                    detail = requireNotNull(detail),
                    instanceMillis = instanceMillis,
                    newStart = newStartMillis,
                    newEnd = outcome.newEndMillis,
                    scope = RecurringEditScope.AllEvents,
                )
            is RescheduleDecision.AskScope ->
                pendingRescheduleBacker.update {
                    PendingReschedule(
                        detail = requireNotNull(detail),
                        instanceMillis = instanceMillis,
                        newStartMillis = newStartMillis,
                        newEndMillis = outcome.newEndMillis,
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
    // the scope-driven draft shape (rrule + anchor-shifted range) is a pure,
    // tested decision: ThisInstance drops the rrule for a one-off, AllEvents shifts
    // the parent anchor by the occurrence delta so earlier occurrences survive.
    // see rescheduleDraftShape / RecurringAnchorTest.
    val shape = rescheduleDraftShape(
        scope = scope,
        parentRrule = detail.rrule,
        parentStartMillis = detail.startMillis,
        instanceStartMillis = instanceMillis,
        newStartMillis = newStart,
        newEndMillis = newEnd,
    )
    val draft = EventDraft(
        calendarId = detail.calendarId,
        title = detail.title,
        description = detail.description,
        location = detail.location,
        startMillis = shape.startMillis,
        endMillis = shape.endMillis,
        allDay = detail.allDay,
        eventTimezone = detail.eventTimezone,
        rrule = shape.rrule,
        status = detail.status,
        availability = detail.availability,
    )
    val updated = eventRepository.updateEvent(
        eventId = detail.eventId,
        draft = draft,
        scope = scope,
        instanceMillis = instanceMillis,
        parentRrule = detail.rrule,
        parentAllDay = detail.allDay,
    )
    // provider rejected the move: snap the optimistic chip back instead of
    // leaving it stranded at a time nothing was written to.
    if (updated == null) dragRevertSignalBacker.tryEmit(detail.eventId)
}
