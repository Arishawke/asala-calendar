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

    // all-day instances are stored at 00:00 UTC but fire at 9am local. once `now`
    // passes 00:00 UTC, the instance start is in the past while the 9am trigger is
    // still in the future, and the reminder must still arm. regression guard: a
    // premature `instanceStartMillis > now` pre-filter dropped the row before the
    // 9am math ran, so a same-day all-day reminder silently never fired in any zone
    // once `now` passed the instance's 00:00 UTC start.
    @Test
    fun `same-day all-day reminder still arms after UTC midnight passes`() {
        val instanceStart =
            java.time.LocalDate
                .of(2026, 6, 1)
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        val nineAmLocal =
            java.time.LocalDateTime
                .of(2026, 6, 1, 9, 0)
                .atZone(ny)
                .toInstant()
                .toEpochMilli()
        val nowAfterUtcMidnight = instanceStart + 7 * 60 * 60_000L // 07:00 UTC, before the 13:00 UTC (9am EDT) trigger
        assertTrue("precondition: now is past the UTC-midnight instance start", nowAfterUtcMidnight > instanceStart)
        assertTrue("precondition: now is before the 9am-local trigger", nowAfterUtcMidnight < nineAmLocal)

        val plan =
            ReminderScheduler.computePlan(
                now = nowAfterUtcMidnight,
                zone = ny,
                reminders = listOf(reminder(9L, instanceStart, 0, allDay = true)),
            )
        assertEquals(1, plan.size)
        assertEquals(nineAmLocal, plan.first().triggerAtMillis)
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

    // moving an event's start time must cancel the alarm at the old time and arm
    // one at the new time. computePlan(old) -> diff -> computePlan(new) yields
    // exactly one cancel and one arm, with the new trigger rebased to the new
    // start. Guards the "edited the time, stale alarm still fires" regression.
    @Test
    fun `editing an event time rebases its alarm`() {
        val oldStart = now + 60 * 60_000L
        val newStart = oldStart + 2 * 60 * 60_000L
        val previous = ReminderScheduler.computePlan(now, ny, listOf(reminder(8L, oldStart, 10)))
        val current = ReminderScheduler.computePlan(now, ny, listOf(reminder(8L, newStart, 10)))
        val (toCancel, toArm) = ReminderScheduler.diff(previous, current)
        assertEquals(previous, toCancel)
        assertEquals(current, toArm)
        assertEquals(newStart - 10 * 60_000L, toArm.first().triggerAtMillis)
    }

    // an occurrence leaving the plan must drop its pending snooze too, deduped to a
    // single (eventId, instance) even when that occurrence carried several reminder
    // rows. Guards the "snoozed, then rescheduled or deleted, snooze still rings"
    // regression: the cancel pass feeds these keys to a forSnoozeAlarm cancel.
    @Test
    fun `snooze cancel keys cover only occurrences leaving the plan`() {
        val survives =
            AlarmKey(eventId = 1L, instanceStartMillis = now + 60_000L, minutesBefore = 10, triggerAtMillis = now)
        val leaving10 =
            AlarmKey(eventId = 2L, instanceStartMillis = now + 120_000L, minutesBefore = 10, triggerAtMillis = now)
        val leaving30 =
            AlarmKey(eventId = 2L, instanceStartMillis = now + 120_000L, minutesBefore = 30, triggerAtMillis = now)
        val (toCancel, _) =
            ReminderScheduler.diff(previous = setOf(survives, leaving10, leaving30), current = setOf(survives))
        val snoozeKeys = ReminderScheduler.snoozeKeysToCancel(toCancel)
        assertEquals(setOf(2L to (now + 120_000L)), snoozeKeys)
        assertTrue((1L to (now + 60_000L)) !in snoozeKeys)
    }
}
