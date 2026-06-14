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

    // "this and following" split: the future series must carry only the
    // occurrences the truncated parent did NOT keep, else COUNT regenerates the
    // full series from the new anchor and the total over-generates
    // ((kept) + (full COUNT) > original). Standard AOSP/Etar + Apple CalDAV
    // behaviour: split COUNT = original - kept.
    @Test fun reduceSplitCount_subtracts_kept_instances_from_count() {
        assertEquals(
            "FREQ=WEEKLY;COUNT=7",
            RecurrenceExceptionMath.reduceSplitCount("FREQ=WEEKLY;COUNT=10", keptInstances = 3),
        )
    }

    // only the COUNT token changes; FREQ/INTERVAL/BYDAY and their order survive.
    @Test fun reduceSplitCount_preserves_other_tokens_and_order() {
        assertEquals(
            "FREQ=WEEKLY;INTERVAL=2;COUNT=6;BYDAY=MO,WE",
            RecurrenceExceptionMath.reduceSplitCount("FREQ=WEEKLY;INTERVAL=2;COUNT=10;BYDAY=MO,WE", keptInstances = 4),
        )
    }

    // UNTIL-bounded and open-ended parents do not over-generate via COUNT, so
    // their split rule is left untouched.
    @Test fun reduceSplitCount_leaves_until_or_open_ended_rules_unchanged() {
        assertEquals(
            "FREQ=DAILY;UNTIL=20260301T235959Z",
            RecurrenceExceptionMath.reduceSplitCount("FREQ=DAILY;UNTIL=20260301T235959Z", keptInstances = 5),
        )
        assertEquals(
            "FREQ=DAILY",
            RecurrenceExceptionMath.reduceSplitCount("FREQ=DAILY", keptInstances = 5),
        )
    }

    // splitting at the first instance keeps nothing, so the split keeps the
    // full original count.
    @Test fun reduceSplitCount_with_zero_kept_keeps_full_count() {
        assertEquals(
            "FREQ=DAILY;COUNT=10",
            RecurrenceExceptionMath.reduceSplitCount("FREQ=DAILY;COUNT=10", keptInstances = 0),
        )
    }

    // a split must contain at least its own (edited) instance; never emit
    // COUNT=0 or a negative count even if kept somehow exceeds the original.
    @Test fun reduceSplitCount_never_drops_below_one() {
        assertEquals(
            "FREQ=DAILY;COUNT=1",
            RecurrenceExceptionMath.reduceSplitCount("FREQ=DAILY;COUNT=3", keptInstances = 5),
        )
    }

    // kept exactly equals the original count: the split keeps only its own edited
    // instance, so COUNT floors to 1 (the coerce boundary, distinct from the
    // already-tested kept > count overflow).
    @Test fun reduceSplitCount_kept_equals_count_floors_to_one() {
        assertEquals(
            "FREQ=DAILY;COUNT=1",
            RecurrenceExceptionMath.reduceSplitCount("FREQ=DAILY;COUNT=5", keptInstances = 5),
        )
    }

    // "delete/edit this occurrence" excludes the slot on the parent via EXDATE
    // (a provider exception row drops the whole series from instance expansion).
    // Timed occurrences exclude by UTC datetime; the format and zone must be
    // exact. 1_700_000_000_000 ms is 2023-11-14T22:13:20Z.
    @Test fun exdateValue_formats_a_timed_occurrence_as_utc_datetime() {
        assertEquals("20231114T221320Z", RecurrenceExceptionMath.exdateValue(1_700_000_000_000L, allDay = false))
    }

    // all-day occurrences exclude by bare date (the slot is UTC midnight).
    @Test fun exdateValue_formats_an_all_day_occurrence_as_a_bare_date() {
        assertEquals("20231114", RecurrenceExceptionMath.exdateValue(1_700_000_000_000L, allDay = true))
    }

    // EXDATE accumulates: the first exclusion seeds the field, later ones append
    // comma-separated, so deleting a second occurrence keeps the first excluded.
    @Test fun mergeExdate_seeds_then_comma_appends() {
        assertEquals("A", RecurrenceExceptionMath.mergeExdate(null, "A"))
        assertEquals("A", RecurrenceExceptionMath.mergeExdate("", "A"))
        assertEquals("X,A", RecurrenceExceptionMath.mergeExdate("X", "A"))
    }

    // re-excluding an already-excluded slot is idempotent: the value isn't
    // appended twice, so EXDATE can't grow without bound across repeats.
    @Test fun mergeExdate_skips_a_value_already_present() {
        assertEquals("A", RecurrenceExceptionMath.mergeExdate("A", "A"))
        assertEquals("X,A", RecurrenceExceptionMath.mergeExdate("X,A", "A"))
        assertEquals("X,A,B", RecurrenceExceptionMath.mergeExdate("X,A", "B"))
    }
}
