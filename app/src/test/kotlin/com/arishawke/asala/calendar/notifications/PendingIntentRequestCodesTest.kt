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
}
