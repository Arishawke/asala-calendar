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

// the drag-reschedule decision, split out of AppViewModel so the table (which
// paths revert the optimistic chip, which save, which open the scope picker) is
// unit-testable without a ViewModel/repo harness. the ViewModel keeps the I/O
// (repo write, revert signal, pending-state update).
sealed interface RescheduleDecision {
    // detail could not be loaded; the chip is stranded at the dragged time.
    data object RevertNoDetail : RescheduleDecision

    // all-day drags are unsupported; snap the chip back.
    data object RevertAllDay : RescheduleDecision

    // the drag landed on the original start: nothing to write, nothing to revert.
    data object NoOp : RescheduleDecision

    // non-recurring: write the move immediately. newEndMillis preserves duration.
    data class Save(val newEndMillis: Long) : RescheduleDecision

    // recurring: open the scope picker before writing. newEndMillis preserves duration.
    data class AskScope(val newEndMillis: Long) : RescheduleDecision
}

// pure decision table behind rescheduleEvent. every abandon path (no detail,
// all-day) maps to a Revert so the caller emits exactly one revert signal.
fun rescheduleOutcome(detail: EventDetail?, newStartMillis: Long): RescheduleDecision = when {
    detail == null -> RescheduleDecision.RevertNoDetail
    detail.allDay -> RescheduleDecision.RevertAllDay
    newStartMillis == detail.startMillis -> RescheduleDecision.NoOp
    else -> {
        val newEnd = newStartMillis + (detail.endMillis - detail.startMillis)
        if (detail.rrule == null) RescheduleDecision.Save(newEnd) else RescheduleDecision.AskScope(newEnd)
    }
}
