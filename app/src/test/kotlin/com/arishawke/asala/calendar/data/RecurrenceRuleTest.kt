/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecurrenceRuleTest {
    @Test fun parses_daily() {
        assertEquals(RecurrenceFrequency.Daily, RecurrenceRule.frequencyOf("FREQ=DAILY"))
    }

    @Test fun parses_weekly() {
        assertEquals(RecurrenceFrequency.Weekly, RecurrenceRule.frequencyOf("FREQ=WEEKLY;BYDAY=MO,WE,FR"))
    }

    @Test fun parses_monthly() {
        assertEquals(RecurrenceFrequency.Monthly, RecurrenceRule.frequencyOf("FREQ=MONTHLY;BYMONTHDAY=15"))
    }

    @Test fun parses_yearly() {
        assertEquals(RecurrenceFrequency.Yearly, RecurrenceRule.frequencyOf("FREQ=YEARLY"))
    }

    @Test fun null_for_unknown_or_empty() {
        assertNull(RecurrenceRule.frequencyOf(""))
        assertNull(RecurrenceRule.frequencyOf("FREQ=HOURLY"))
        assertNull(RecurrenceRule.frequencyOf(null))
    }

    @Test fun builds_daily_no_end() {
        val r = RecurrenceRule.build(RecurrenceFrequency.Daily, interval = 1, untilUtc = null, count = null)
        assertEquals("FREQ=DAILY", r)
    }

    @Test fun builds_weekly_until() {
        val r =
            RecurrenceRule.build(
                frequency = RecurrenceFrequency.Weekly,
                interval = 1,
                untilUtc = LocalDate.of(2026, 12, 31),
                count = null,
            )
        assertEquals("FREQ=WEEKLY;UNTIL=20261231T235959Z", r)
    }

    @Test fun builds_monthly_count() {
        val r = RecurrenceRule.build(RecurrenceFrequency.Monthly, interval = 1, untilUtc = null, count = 12)
        assertEquals("FREQ=MONTHLY;COUNT=12", r)
    }

    @Test fun builds_yearly_interval_2() {
        val r = RecurrenceRule.build(RecurrenceFrequency.Yearly, interval = 2, untilUtc = null, count = null)
        assertEquals("FREQ=YEARLY;INTERVAL=2", r)
    }

    @Test fun interval_defaults_to_one() {
        assertEquals(1, RecurrenceRule.intervalOf("FREQ=WEEKLY"))
        assertEquals(1, RecurrenceRule.intervalOf(null))
        assertEquals(1, RecurrenceRule.intervalOf(""))
    }

    @Test fun interval_parsed_when_present() {
        assertEquals(2, RecurrenceRule.intervalOf("FREQ=WEEKLY;INTERVAL=2"))
        assertEquals(3, RecurrenceRule.intervalOf("FREQ=MONTHLY;INTERVAL=3;BYMONTHDAY=15"))
    }

    @Test fun count_parsed_or_null() {
        assertEquals(10, RecurrenceRule.countOf("FREQ=DAILY;COUNT=10"))
        assertEquals(null, RecurrenceRule.countOf("FREQ=DAILY"))
        assertEquals(null, RecurrenceRule.countOf(null))
    }

    @Test fun until_date_parses_timed_utc_form() {
        assertEquals(
            LocalDate.of(2026, 12, 31),
            RecurrenceRule.untilDateOf("FREQ=WEEKLY;UNTIL=20261231T235959Z"),
        )
    }

    @Test fun until_date_parses_all_day_form() {
        assertEquals(
            LocalDate.of(2026, 12, 31),
            RecurrenceRule.untilDateOf("FREQ=DAILY;UNTIL=20261231"),
        )
    }

    @Test fun until_date_null_when_absent_or_malformed() {
        assertNull(RecurrenceRule.untilDateOf("FREQ=DAILY"))
        assertNull(RecurrenceRule.untilDateOf("FREQ=DAILY;UNTIL=garbage"))
        assertNull(RecurrenceRule.untilDateOf(null))
    }

    // RFC 5545 §3.3.10: UNTIL must match DTSTART value type. All-day DTSTART
    // is a DATE, so UNTIL must be YYYYMMDD without the T...Z suffix.
    @Test fun builds_all_day_until_in_date_form() {
        val r =
            RecurrenceRule.build(
                frequency = RecurrenceFrequency.Daily,
                interval = 1,
                untilUtc = LocalDate.of(2026, 12, 31),
                count = null,
                allDay = true,
            )
        assertEquals("FREQ=DAILY;UNTIL=20261231", r)
    }

    @Test fun timed_until_keeps_datetime_form() {
        val r =
            RecurrenceRule.build(
                frequency = RecurrenceFrequency.Weekly,
                interval = 1,
                untilUtc = LocalDate.of(2026, 12, 31),
                count = null,
                allDay = false,
            )
        assertEquals("FREQ=WEEKLY;UNTIL=20261231T235959Z", r)
    }
}
