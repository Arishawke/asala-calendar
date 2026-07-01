/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// pure resolution behind enable() idempotency (F12) and the deleted-calendar
// self-heal (F5): reuse a stored occasion calendar only while it still exists in
// the provider, otherwise signal a re-create (null).
class OccasionProvisionerTest {
    @Test fun `reuses a stored calendar that still exists`() {
        assertEquals(7L, resolveOccasionCalendarId(storedId = 7L, existingIds = setOf(7L, 9L)))
    }

    @Test fun `signals re-create when the stored calendar was deleted outside the app`() {
        assertNull(resolveOccasionCalendarId(storedId = 7L, existingIds = setOf(9L)))
    }

    @Test fun `signals create when nothing was ever provisioned`() {
        assertNull(resolveOccasionCalendarId(storedId = null, existingIds = setOf(9L)))
    }
}
