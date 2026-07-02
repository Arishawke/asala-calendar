/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// the fire-time liveness check is the only thing standing between an armed
// snooze (or a stale alarm) and a ghost notification: the scheduler's
// snooze-cancel heuristic cannot reach a snooze whose plan key already fired.
// these tests pin the exact-match semantics that suppression relies on.
class ReminderFireLivenessTest {
    private val begin = 1_750_000_000_000L

    @Test
    fun `exact live occurrence match rings`() {
        val rows = listOf(FireWindowInstance(eventId = 1L, beginMillis = begin, cancelled = false))
        assertTrue(anyLiveOccurrenceMatches(rows, eventId = 1L, instanceMillis = begin))
    }

    // a deleted event (or an EXDATE'd single occurrence) leaves no instance row
    // at the armed time, so nothing in the window matches and the ghost is
    // suppressed.
    @Test
    fun `missing occurrence is suppressed`() {
        assertFalse(anyLiveOccurrenceMatches(emptyList(), eventId = 1L, instanceMillis = begin))
    }

    // a moved occurrence keeps its event id but not its BEGIN; ringing would use
    // the stale time, so the match must require the exact armed instant.
    @Test
    fun `moved occurrence with a different begin is suppressed`() {
        val rows = listOf(FireWindowInstance(eventId = 1L, beginMillis = begin + 60_000L, cancelled = false))
        assertFalse(anyLiveOccurrenceMatches(rows, eventId = 1L, instanceMillis = begin))
    }

    @Test
    fun `cancelled occurrence is suppressed`() {
        val rows = listOf(FireWindowInstance(eventId = 1L, beginMillis = begin, cancelled = true))
        assertFalse(anyLiveOccurrenceMatches(rows, eventId = 1L, instanceMillis = begin))
    }

    // the window query returns every instance overlapping the armed instant;
    // an unrelated event running through it must not satisfy the check.
    @Test
    fun `another event overlapping the window does not match`() {
        val rows = listOf(FireWindowInstance(eventId = 2L, beginMillis = begin, cancelled = false))
        assertFalse(anyLiveOccurrenceMatches(rows, eventId = 1L, instanceMillis = begin))
    }
}
