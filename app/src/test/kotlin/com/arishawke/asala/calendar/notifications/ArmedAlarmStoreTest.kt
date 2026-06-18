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

class ArmedAlarmStoreTest {
    private fun key(eventId: Long, instance: Long, minutes: Int, trigger: Long) =
        AlarmKey(eventId = eventId, instanceStartMillis = instance, minutesBefore = minutes, triggerAtMillis = trigger)

    @Test
    fun `encodes a key as colon-joined fields`() {
        assertEquals("1:100:10:90", ArmedAlarmStore.encodeKey(key(1L, 100L, 10, 90L)))
    }

    // persisting the full key (including triggerAtMillis) keeps the diff idempotent:
    // an unchanged plan reloads to an identical set, so a cold start does not churn.
    @Test
    fun `round-trips a plan through encode and decode`() {
        val plan = setOf(
            key(1L, 100L, 10, 90L),
            key(2L, 1L shl 32, 30, (1L shl 32) - 1_800_000L),
            key(7L, 200L, 0, 200L),
        )
        assertEquals(plan, ArmedAlarmStore.decodeKeys(plan.map(ArmedAlarmStore::encodeKey).toSet()))
    }

    // a corrupt stored entry must not crash the reload; it is skipped so the rest
    // of the armed set survives.
    @Test
    fun `drops malformed entries on decode`() {
        val valid = key(1L, 100L, 10, 90L)
        val decoded = ArmedAlarmStore.decodeKeys(
            setOf(
                ArmedAlarmStore.encodeKey(valid),
                "garbage",
                "1:2:3",
                "x:y:z:w",
                "1:100:10:90:extra",
            ),
        )
        assertEquals(setOf(valid), decoded)
    }

    @Test
    fun `empty set round-trips`() {
        assertTrue(ArmedAlarmStore.decodeKeys(emptySet()).isEmpty())
        assertTrue(emptySet<AlarmKey>().map(ArmedAlarmStore::encodeKey).isEmpty())
    }
}
