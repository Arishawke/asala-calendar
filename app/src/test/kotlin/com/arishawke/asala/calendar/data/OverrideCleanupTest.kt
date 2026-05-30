/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Pins the per-event override cleanup rule. AllEvents removes the
// underlying event row, so the override must be cleared; the other
// scopes leave the original eventId intact and the override is still
// the user's intended choice.
class OverrideCleanupTest {
    @Test
    fun `clear on AllEvents delete`() {
        assertTrue(shouldClearEventOverrideOnDelete(RecurringEditScope.AllEvents))
    }

    @Test
    fun `keep on ThisInstance delete`() {
        assertFalse(shouldClearEventOverrideOnDelete(RecurringEditScope.ThisInstance))
    }

    @Test
    fun `keep on ThisAndFollowing delete`() {
        assertFalse(shouldClearEventOverrideOnDelete(RecurringEditScope.ThisAndFollowing))
    }
}
