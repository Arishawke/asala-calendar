/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PendingIntentRequestCodesTest {
    // two pending instances of one recurring event, and two offsets of one
    // occurrence, must each get distinct notification ids or one silently
    // replaces the other in the shade.
    @Test
    fun `forNotification distinguishes instances of the same event`() {
        assertNotEquals(
            PendingIntentRequestCodes.forNotification(1L, 100L, 10),
            PendingIntentRequestCodes.forNotification(1L, 200L, 10),
        )
    }

    @Test
    fun `forNotification distinguishes reminder offsets for the same instance`() {
        assertNotEquals(
            PendingIntentRequestCodes.forNotification(1L, 100L, 10),
            PendingIntentRequestCodes.forNotification(1L, 100L, 30),
        )
    }

    @Test
    fun `forNotification is stable for the same instance and offset`() {
        assertEquals(
            PendingIntentRequestCodes.forNotification(1L, 100L, 10),
            PendingIntentRequestCodes.forNotification(1L, 100L, 10),
        )
    }

    @Test
    fun `forNotification distinguishes different events`() {
        assertNotEquals(
            PendingIntentRequestCodes.forNotification(1L, 100L, 10),
            PendingIntentRequestCodes.forNotification(2L, 100L, 10),
        )
    }

    // each notification's action buttons must stay independent, or two offsets of
    // one occurrence collapse their dismiss/snooze PendingIntents under
    // FLAG_UPDATE_CURRENT and one button drives the wrong shade entry.
    @Test
    fun `dismiss and snooze actions distinguish reminder offsets`() {
        assertNotEquals(
            PendingIntentRequestCodes.forDismiss(1L, 100L, 10),
            PendingIntentRequestCodes.forDismiss(1L, 100L, 30),
        )
        assertNotEquals(
            PendingIntentRequestCodes.forSnoozeDefault(1L, 100L, 10),
            PendingIntentRequestCodes.forSnoozeDefault(1L, 100L, 30),
        )
        assertNotEquals(
            PendingIntentRequestCodes.forSnoozePicker(1L, 100L, 10),
            PendingIntentRequestCodes.forSnoozePicker(1L, 100L, 30),
        )
    }

    // guards against regressing to a Long.toInt() request code, which dropped the
    // upper 32 bits so two instances of one recurring event 1<<32 ms (~49.7 days)
    // apart collided and one silently overwrote the other.
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

    // the receiver must coerce -1 before computing ids: forNotification itself does
    // not, so a raw -1 offset would post/cancel under a different id than the
    // stored CalendarAlerts.MINUTES (always coerced to 0), orphaning the shade entry.
    @Test
    fun `negative offset coerces to the same shade id as the stored alert minutes`() {
        assertEquals(
            PendingIntentRequestCodes.forNotification(1L, 100L, (-1).coerceAtLeast(0)),
            PendingIntentRequestCodes.forNotification(1L, 100L, 0),
        )
        assertNotEquals(
            PendingIntentRequestCodes.forNotification(1L, 100L, 0),
            PendingIntentRequestCodes.forNotification(1L, 100L, -1),
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
            PendingIntentRequestCodes.forSnoozeDefault(1L, 100L, 10),
            PendingIntentRequestCodes.forSnoozePicker(1L, 100L, 10),
            PendingIntentRequestCodes.forDismiss(1L, 100L, 10),
            PendingIntentRequestCodes.forNotification(1L, 100L, 10),
        )
        assertEquals(codes.size, codes.toSet().size)
    }
}
