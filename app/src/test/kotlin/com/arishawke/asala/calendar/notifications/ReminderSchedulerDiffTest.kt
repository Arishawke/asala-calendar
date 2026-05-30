/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ReminderSchedulerDiffTest {
    private val ny = ZoneId.of("America/New_York")
    private val now = 1_750_000_000_000L // arbitrary deterministic clock

    private fun reminder(eventId: Long, startMillis: Long, minutes: Int, allDay: Boolean = false): ScheduledReminder =
        ScheduledReminder(
            eventId = eventId,
            instanceStartMillis = startMillis,
            minutesBefore = minutes,
            allDay = allDay,
            cancelled = false,
        )

    @Test
    fun `plan is empty when no reminders provided`() {
        val plan = ReminderScheduler.computePlan(now = now, zone = ny, reminders = emptyList())
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `single future reminder produces one alarm key`() {
        val start = now + 60 * 60_000L
        val plan = ReminderScheduler.computePlan(now = now, zone = ny, reminders = listOf(reminder(1L, start, 10)))
        assertEquals(1, plan.size)
        val key = plan.first()
        assertEquals(1L, key.eventId)
        assertEquals(start, key.instanceStartMillis)
        assertEquals(10, key.minutesBefore)
        assertEquals(start - 10 * 60_000L, key.triggerAtMillis)
    }

    @Test
    fun `past instance is skipped`() {
        val start = now - 60 * 60_000L
        val plan = ReminderScheduler.computePlan(now = now, zone = ny, reminders = listOf(reminder(2L, start, 10)))
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `cancelled exception instance is skipped`() {
        val start = now + 60 * 60_000L
        val cancelled = reminder(3L, start, 10).copy(cancelled = true)
        val plan = ReminderScheduler.computePlan(now = now, zone = ny, reminders = listOf(cancelled))
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `duplicate reminder rows produce only one alarm key`() {
        val start = now + 60 * 60_000L
        val r = reminder(4L, start, 10)
        val plan = ReminderScheduler.computePlan(now = now, zone = ny, reminders = listOf(r, r))
        assertEquals(1, plan.size)
    }

    @Test
    fun `idempotent run produces identical plans`() {
        val a = reminder(5L, now + 60 * 60_000L, 10)
        val b = reminder(6L, now + 2 * 60 * 60_000L, 30)
        val first = ReminderScheduler.computePlan(now = now, zone = ny, reminders = listOf(a, b))
        val second = ReminderScheduler.computePlan(now = now, zone = ny, reminders = listOf(a, b))
        assertEquals(first, second)
    }

    @Test
    fun `all-day reminder uses 9am anchor not midnight`() {
        val start =
            java.time.LocalDate
                .of(2026, 6, 1)
                .atStartOfDay(ny)
                .toInstant()
                .toEpochMilli()
        val plan =
            ReminderScheduler.computePlan(
                now = start - 24 * 60 * 60_000L,
                zone = ny,
                reminders = listOf(reminder(7L, start, 0, allDay = true)),
            )
        assertEquals(1, plan.size)
        val expected =
            java.time.LocalDateTime
                .of(2026, 6, 1, 9, 0)
                .atZone(ny)
                .toInstant()
                .toEpochMilli()
        assertEquals(expected, plan.first().triggerAtMillis)
    }

    @Test
    fun `diff identifies keys removed from previous plan`() {
        val a = AlarmKey(eventId = 1L, instanceStartMillis = now + 60_000L, minutesBefore = 10, triggerAtMillis = now)
        val b = AlarmKey(eventId = 2L, instanceStartMillis = now + 120_000L, minutesBefore = 10, triggerAtMillis = now)
        val (toCancel, toArm) = ReminderScheduler.diff(previous = setOf(a, b), current = setOf(a))
        assertEquals(setOf(b), toCancel)
        assertTrue(toArm.isEmpty())
    }

    @Test
    fun `diff identifies keys added in current plan`() {
        val a = AlarmKey(eventId = 1L, instanceStartMillis = now + 60_000L, minutesBefore = 10, triggerAtMillis = now)
        val b = AlarmKey(eventId = 2L, instanceStartMillis = now + 120_000L, minutesBefore = 10, triggerAtMillis = now)
        val (toCancel, toArm) = ReminderScheduler.diff(previous = setOf(a), current = setOf(a, b))
        assertTrue(toCancel.isEmpty())
        assertEquals(setOf(b), toArm)
    }

    @Test
    fun `diff returns empty sets when plans are identical (no-op reschedule)`() {
        val a = AlarmKey(eventId = 1L, instanceStartMillis = now + 60_000L, minutesBefore = 10, triggerAtMillis = now)
        val (toCancel, toArm) = ReminderScheduler.diff(previous = setOf(a), current = setOf(a))
        assertTrue(toCancel.isEmpty())
        assertTrue(toArm.isEmpty())
    }
}
