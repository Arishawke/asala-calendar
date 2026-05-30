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
import org.junit.Test

class RecurringExceptionMathTest {
    @Test fun untilForTruncation_is_one_second_before_instance_for_timed() {
        val instanceMillis = 1_700_000_000_000L
        val until = RecurrenceExceptionMath.untilUtcForTruncation(instanceMillis)
        // UNTIL must be strictly before the truncated instance.
        // Subtract 1 second so the formatted seconds value differs and the
        // cutoff instance is genuinely excluded from the series.
        // 1_699_999_999_000L in UTC is 2023-11-14T22:13:19Z.
        assertEquals("UNTIL=20231114T221319Z", until)
    }

    // RFC 5545 §3.3.10: when the parent series is all-day, UNTIL must be a
    // DATE (YYYYMMDD), not a DATE-TIME. Date-form must point at the day
    // BEFORE the truncated instance so it stays excluded.
    @Test fun untilForTruncation_emits_date_form_when_all_day() {
        // 2023-11-14T22:13:20Z is the same instance millis; for an all-day
        // parent the recurrence engine produced this at UTC midnight of
        // 2023-11-14. The cutoff date is 2023-11-13.
        val instanceMillis = 1_700_000_000_000L
        val until = RecurrenceExceptionMath.untilUtcForTruncation(instanceMillis, allDay = true)
        assertEquals("UNTIL=20231113", until)
    }

    // For an all-day instance that sits at UTC midnight, the cutoff is
    // the calendar day before that midnight.
    @Test fun untilForTruncation_all_day_handles_utc_midnight_instance() {
        // 2024-01-15T00:00:00Z = 1_705_276_800_000L. All-day parent's
        // engine emits this for a 2024-01-15 occurrence. Cutoff = 2024-01-14.
        val instanceMillis = 1_705_276_800_000L
        val until = RecurrenceExceptionMath.untilUtcForTruncation(instanceMillis, allDay = true)
        assertEquals("UNTIL=20240114", until)
    }

    @Test fun appendUntil_replaces_existing_until_or_count() {
        assertEquals(
            "FREQ=WEEKLY;UNTIL=20260301T085959Z",
            RecurrenceExceptionMath.appendUntil("FREQ=WEEKLY", "UNTIL=20260301T085959Z"),
        )
        assertEquals(
            "FREQ=WEEKLY;UNTIL=20260301T085959Z",
            RecurrenceExceptionMath.appendUntil("FREQ=WEEKLY;UNTIL=20271231T235959Z", "UNTIL=20260301T085959Z"),
        )
        assertEquals(
            "FREQ=WEEKLY;UNTIL=20260301T085959Z",
            RecurrenceExceptionMath.appendUntil("FREQ=WEEKLY;COUNT=10", "UNTIL=20260301T085959Z"),
        )
    }
}
