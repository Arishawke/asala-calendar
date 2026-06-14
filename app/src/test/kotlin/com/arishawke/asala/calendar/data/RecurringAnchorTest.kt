/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecurringAnchorTest {
    // the bug this guards: applying an "all events" reschedule/edit from a
    // non-first occurrence must move the whole series by the occurrence's delta,
    // not pin the parent anchor to that occurrence's new absolute time (which
    // would jump the series start forward and silently drop every earlier
    // occurrence). parent starts at 1_000; the user moved the 8_000 occurrence to
    // 9_000 (+1_000), so the parent must move to 2_000, keeping its 3_600 duration.
    @Test
    fun `shifts the parent anchor by the occurrence delta`() {
        assertEquals(
            2_000L to 5_600L,
            allEventsAnchorRange(
                parentStartMillis = 1_000L,
                instanceStartMillis = 8_000L,
                newStartMillis = 9_000L,
                newEndMillis = 12_600L,
            ),
        )
    }

    // first occurrence (or a non-recurring event): the dragged/edited instance IS
    // the parent anchor, so the new time is written through unchanged. this is the
    // identity case that lets callers apply the shift unconditionally for AllEvents.
    @Test
    fun `first-occurrence move writes the new time directly`() {
        assertEquals(
            5_000L to 8_600L,
            allEventsAnchorRange(
                parentStartMillis = 1_000L,
                instanceStartMillis = 1_000L,
                newStartMillis = 5_000L,
                newEndMillis = 8_600L,
            ),
        )
    }

    // a title-only or duration-only edit leaves the occurrence start unmoved
    // (delta 0), so the parent anchor must stay put rather than jump to the opened
    // occurrence. the edited duration still carries to the series.
    @Test
    fun `unmoved occurrence keeps the parent start and carries the new duration`() {
        assertEquals(
            1_000L to 8_200L,
            allEventsAnchorRange(
                parentStartMillis = 1_000L,
                instanceStartMillis = 8_000L,
                newStartMillis = 8_000L,
                newEndMillis = 15_200L,
            ),
        )
    }

    // dragging a later occurrence earlier shifts the whole series back by the
    // negative delta; the parent anchor moves earlier too, not to the occurrence.
    @Test
    fun `negative delta shifts the series earlier`() {
        assertEquals(
            4_000L to 7_600L,
            allEventsAnchorRange(
                parentStartMillis = 5_000L,
                instanceStartMillis = 8_000L,
                newStartMillis = 7_000L,
                newEndMillis = 10_600L,
            ),
        )
    }

    // the drag-reschedule draft shape (previously inlined and untested in
    // saveRescheduleNow). ThisInstance writes a one-off (no rrule) at the dragged
    // time; ThisAndFollowing keeps the rrule and writes the new time as-is.
    @Test
    fun `drag this-instance drops the rrule and writes the dragged time`() {
        assertEquals(
            RescheduleDraftShape(rrule = null, startMillis = 9_000L, endMillis = 12_600L),
            rescheduleDraftShape(
                scope = RecurringEditScope.ThisInstance,
                parentRrule = "FREQ=DAILY",
                parentStartMillis = 1_000L,
                instanceStartMillis = 8_000L,
                newStartMillis = 9_000L,
                newEndMillis = 12_600L,
            ),
        )
    }

    @Test
    fun `drag this-and-following keeps the rrule and writes the new time as-is`() {
        assertEquals(
            RescheduleDraftShape(rrule = "FREQ=DAILY", startMillis = 9_000L, endMillis = 12_600L),
            rescheduleDraftShape(
                scope = RecurringEditScope.ThisAndFollowing,
                parentRrule = "FREQ=DAILY",
                parentStartMillis = 1_000L,
                instanceStartMillis = 8_000L,
                newStartMillis = 9_000L,
                newEndMillis = 12_600L,
            ),
        )
    }

    // the data-loss guard on the drag path: AllEvents from a later occurrence keeps
    // the rrule and shifts the PARENT anchor by the delta, not to the occurrence.
    @Test
    fun `drag all-events shifts the parent anchor and keeps the rrule`() {
        assertEquals(
            RescheduleDraftShape(rrule = "FREQ=DAILY", startMillis = 2_000L, endMillis = 5_600L),
            rescheduleDraftShape(
                scope = RecurringEditScope.AllEvents,
                parentRrule = "FREQ=DAILY",
                parentStartMillis = 1_000L,
                instanceStartMillis = 8_000L,
                newStartMillis = 9_000L,
                newEndMillis = 12_600L,
            ),
        )
    }

    // AllEvents on a non-recurring drag (the occurrence IS the parent, rrule null):
    // the anchor shift is the identity, so the new time writes through unchanged.
    @Test
    fun `drag all-events on a non-recurring event writes the new time directly`() {
        assertEquals(
            RescheduleDraftShape(rrule = null, startMillis = 5_000L, endMillis = 8_600L),
            rescheduleDraftShape(
                scope = RecurringEditScope.AllEvents,
                parentRrule = null,
                parentStartMillis = 1_000L,
                instanceStartMillis = 1_000L,
                newStartMillis = 5_000L,
                newEndMillis = 8_600L,
            ),
        )
    }
}
