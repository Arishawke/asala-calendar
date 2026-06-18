/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PendingIntentRequestCodesTest {
    // two pending instances of one recurring event must get distinct notification
    // ids, or the second reminder silently replaces the first in the shade.
    @Test
    fun `forNotification distinguishes instances of the same event`() {
        assertNotEquals(
            PendingIntentRequestCodes.forNotification(1L, 100L),
            PendingIntentRequestCodes.forNotification(1L, 200L),
        )
    }

    @Test
    fun `forNotification is stable for the same instance`() {
        assertEquals(
            PendingIntentRequestCodes.forNotification(1L, 100L),
            PendingIntentRequestCodes.forNotification(1L, 100L),
        )
    }

    @Test
    fun `forNotification distinguishes different events`() {
        assertNotEquals(
            PendingIntentRequestCodes.forNotification(1L, 100L),
            PendingIntentRequestCodes.forNotification(2L, 100L),
        )
    }

    // the documented collision: a Long.toInt() request code drops the upper 32
    // bits, so two instances of one recurring event 1<<32 ms (~49.7 days) apart
    // mapped to the same code and one silently overwrote the other.
    @Test
    fun `forAlarm distinguishes instances ~49 days apart for the same event and offset`() {
        assertNotEquals(
            PendingIntentRequestCodes.forAlarm(1L, 0L, 10),
            PendingIntentRequestCodes.forAlarm(1L, 1L shl 32, 10),
        )
    }

    @Test
    fun `forAlarm distinguishes reminder offsets for the same instance`() {
        assertNotEquals(
            PendingIntentRequestCodes.forAlarm(1L, 100L, 10),
            PendingIntentRequestCodes.forAlarm(1L, 100L, 30),
        )
    }

    // stable so a re-arm under FLAG_UPDATE_CURRENT updates the alarm in place.
    @Test
    fun `forAlarm is stable for identical inputs`() {
        assertEquals(
            PendingIntentRequestCodes.forAlarm(1L, 100L, 10),
            PendingIntentRequestCodes.forAlarm(1L, 100L, 10),
        )
    }

    @Test
    fun `forSnoozeAlarm distinguishes instances of the same event`() {
        assertNotEquals(
            PendingIntentRequestCodes.forSnoozeAlarm(1L, 100L),
            PendingIntentRequestCodes.forSnoozeAlarm(1L, 200L),
        )
    }

    @Test
    fun `forSnoozeAlarm is stable for the same instance`() {
        assertEquals(
            PendingIntentRequestCodes.forSnoozeAlarm(1L, 100L),
            PendingIntentRequestCodes.forSnoozeAlarm(1L, 100L),
        )
    }

    // a snooze must not share the reminder's slot: a routine rescheduleAll cancels
    // the original forAlarm(event, instance, minutes) slot, and a "0 minutes
    // before" reminder makes minutes == the snooze fallback, so an overlapping
    // namespace lets a reschedule cancel a pending snooze.
    @Test
    fun `snooze alarms use a different slot than the matching reminder`() {
        assertNotEquals(
            PendingIntentRequestCodes.forSnoozeAlarm(1L, 100L),
            PendingIntentRequestCodes.forAlarm(1L, 100L, 0),
        )
    }

    // every request-code namespace must be distinct for the same ids, or two
    // unrelated PendingIntents collide under FLAG_UPDATE_CURRENT.
    @Test
    fun `all request-code namespaces are distinct for the same ids`() {
        val codes = listOf(
            PendingIntentRequestCodes.forAlarm(1L, 100L, 10),
            PendingIntentRequestCodes.forSnoozeAlarm(1L, 100L),
            PendingIntentRequestCodes.forOpen(1L, 100L),
            PendingIntentRequestCodes.forSnoozeDefault(1L, 100L),
            PendingIntentRequestCodes.forSnoozePicker(1L, 100L),
            PendingIntentRequestCodes.forDismiss(1L, 100L),
            PendingIntentRequestCodes.forNotification(1L, 100L),
        )
        assertEquals(codes.size, codes.toSet().size)
    }
}
