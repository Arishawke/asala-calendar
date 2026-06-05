/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar

import com.arishawke.asala.calendar.data.EventDetail
import org.junit.Assert.assertEquals
import org.junit.Test

class RescheduleDecisionTest {
    private fun detail(startMillis: Long, endMillis: Long, allDay: Boolean = false, rrule: String? = null) =
        EventDetail(
            eventId = 1L,
            calendarId = 1L,
            title = "Standup",
            description = null,
            location = null,
            startMillis = startMillis,
            endMillis = endMillis,
            allDay = allDay,
            eventTimezone = "UTC",
            rrule = rrule,
            displayColor = 0,
            calendarDisplayName = "Personal",
            reminderMinutesBefore = null,
        )

    // a drag whose detail could not be loaded must revert the optimistic chip:
    // it is stranded at the dragged time with no write behind it.
    @Test
    fun `missing detail reverts`() {
        assertEquals(RescheduleDecision.RevertNoDetail, rescheduleOutcome(null, 5_000L))
    }

    // all-day drags are unsupported, so the chip snaps back rather than half-moving.
    @Test
    fun `all-day drag reverts`() {
        val d = detail(startMillis = 0L, endMillis = 86_400_000L, allDay = true)
        assertEquals(RescheduleDecision.RevertAllDay, rescheduleOutcome(d, 3_600_000L))
    }

    // a drag that lands back on the original start writes nothing and, because the
    // chip never moved, must not revert either.
    @Test
    fun `unchanged start is a no-op`() {
        val d = detail(startMillis = 1_000L, endMillis = 4_600_000L)
        assertEquals(RescheduleDecision.NoOp, rescheduleOutcome(d, 1_000L))
    }

    // a moved non-recurring event saves immediately, preserving its duration.
    @Test
    fun `non-recurring move saves with preserved duration`() {
        val d = detail(startMillis = 1_000L, endMillis = 3_601_000L) // 1h long
        assertEquals(RescheduleDecision.Save(10_800_000L), rescheduleOutcome(d, 7_200_000L))
    }

    // a moved recurring event opens the scope picker before any write, also
    // carrying the duration-preserving new end.
    @Test
    fun `recurring move asks for scope with preserved duration`() {
        val d = detail(startMillis = 1_000L, endMillis = 3_601_000L, rrule = "FREQ=DAILY")
        assertEquals(RescheduleDecision.AskScope(10_800_000L), rescheduleOutcome(d, 7_200_000L))
    }
}
