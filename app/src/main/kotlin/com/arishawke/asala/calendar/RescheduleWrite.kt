/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar

import com.arishawke.asala.calendar.data.EventDetail
import com.arishawke.asala.calendar.data.EventDraft
import com.arishawke.asala.calendar.data.RecurringEditScope
import com.arishawke.asala.calendar.data.rescheduleDraftShape

// the I/O half of a drag-reschedule, split out so the revert-on-rejection contract
// is unit-testable without an AppViewModel or a ContentResolver (mirrors how
// EventSave.attempt takes injected lambdas). the draft shaping is the pure,
// separately-tested rescheduleDraftShape (RecurringAnchorTest).
@Suppress("LongParameterList") // the inputs + injected update/revert seams
internal suspend fun rescheduleWrite(
    detail: EventDetail,
    instanceMillis: Long,
    newStart: Long,
    newEnd: Long,
    scope: RecurringEditScope,
    updateEvent: suspend (Long, EventDraft, RecurringEditScope, Long?, String?, Boolean) -> Long?,
    onRevert: (Long) -> Unit,
) {
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
    val updated = updateEvent(detail.eventId, draft, scope, instanceMillis, detail.rrule, detail.allDay)
    // provider rejected the move: snap the optimistic chip back instead of leaving
    // it stranded at a time nothing was written to.
    if (updated == null) onRevert(detail.eventId)
}
