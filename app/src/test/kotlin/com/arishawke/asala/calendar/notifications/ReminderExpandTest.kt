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

// guards the batched-query join: the old code emitted one reminder per
// (instance, minutes) by querying Reminders per instance. expandReminders must
// reproduce that fan-out from a single per-event minutes map.
class ReminderExpandTest {
    private fun instance(eventId: Long, start: Long, allDay: Boolean = false): ReminderInstance =
        ReminderInstance(eventId = eventId, instanceStartMillis = start, allDay = allDay)

    @Test
    fun `each instance fans out across its event's reminders`() {
        val instances = listOf(instance(1L, 100L), instance(1L, 200L))
        val byEvent = mapOf(1L to listOf(10, 60))

        val out = ReminderScheduler.expandReminders(instances, byEvent)

        // 2 instances x 2 reminders = 4 rows, each carrying its own instance start.
        assertEquals(4, out.size)
        assertEquals(setOf(100L, 200L), out.map { it.instanceStartMillis }.toSet())
        assertEquals(setOf(10, 60), out.map { it.minutesBefore }.toSet())
    }

    @Test
    fun `events with no reminders contribute nothing`() {
        val instances = listOf(instance(1L, 100L), instance(2L, 300L))
        val byEvent = mapOf(1L to listOf(15)) // event 2 has no reminders

        val out = ReminderScheduler.expandReminders(instances, byEvent)

        assertEquals(1, out.size)
        assertEquals(1L, out.single().eventId)
    }

    @Test
    fun `reminders map back to their own event`() {
        val instances = listOf(instance(1L, 100L), instance(2L, 300L, allDay = true))
        val byEvent = mapOf(1L to listOf(10), 2L to listOf(1440))

        val out = ReminderScheduler.expandReminders(instances, byEvent)

        val byId = out.associateBy { it.eventId }
        assertEquals(10, byId.getValue(1L).minutesBefore)
        assertEquals(1440, byId.getValue(2L).minutesBefore)
        assertTrue(byId.getValue(2L).allDay)
    }
}
