/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class EventDetailReaderTest {
    @Test fun all_day_row_with_null_timezone_resolves_to_utc() {
        // all-day dates are stored in UTC; coercing a null tz to the device zone
        // would shift the dates if the all-day toggle is later flipped off.
        assertEquals("UTC", resolveEventTimezone(stored = null, allDay = true))
    }

    @Test fun timed_row_with_null_timezone_falls_back_to_device_zone() {
        assertEquals(TimeZone.getDefault().id, resolveEventTimezone(stored = null, allDay = false))
    }

    @Test fun stored_timezone_is_used_verbatim() {
        assertEquals("America/New_York", resolveEventTimezone(stored = "America/New_York", allDay = true))
        assertEquals("Europe/London", resolveEventTimezone(stored = "Europe/London", allDay = false))
    }
}
