/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

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
        val r = RecurrenceRule.build(RecurrenceFrequency.Daily, interval = 1, untilDate = null, count = null)
        assertEquals("FREQ=DAILY", r)
    }

    @Test fun builds_weekly_until() {
        val r =
            RecurrenceRule.build(
                frequency = RecurrenceFrequency.Weekly,
                interval = 1,
                untilDate = LocalDate.of(2026, 12, 31),
                count = null,
            )
        assertEquals("FREQ=WEEKLY;UNTIL=20261231T235959Z", r)
    }

    @Test fun builds_monthly_count() {
        val r = RecurrenceRule.build(RecurrenceFrequency.Monthly, interval = 1, untilDate = null, count = 12)
        assertEquals("FREQ=MONTHLY;COUNT=12", r)
    }

    @Test fun builds_yearly_interval_2() {
        val r = RecurrenceRule.build(RecurrenceFrequency.Yearly, interval = 2, untilDate = null, count = null)
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

    // malformed imported / CalDAV rules: a non-positive interval is meaningless,
    // so fall back to 1 rather than handing 0 or a negative to the provider.
    @Test fun interval_non_positive_falls_back_to_one() {
        assertEquals(1, RecurrenceRule.intervalOf("FREQ=DAILY;INTERVAL=0"))
        assertEquals(1, RecurrenceRule.intervalOf("FREQ=DAILY;INTERVAL=-2"))
    }

    // a non-positive COUNT is treated as no count (open-ended), never a
    // zero-occurrence series.
    @Test fun count_non_positive_is_null() {
        assertNull(RecurrenceRule.countOf("FREQ=DAILY;COUNT=0"))
        assertNull(RecurrenceRule.countOf("FREQ=DAILY;COUNT=-3"))
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
                untilDate = LocalDate.of(2026, 12, 31),
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
                untilDate = LocalDate.of(2026, 12, 31),
                count = null,
                allDay = false,
            )
        assertEquals("FREQ=WEEKLY;UNTIL=20261231T235959Z", r)
    }

    // matchesEditorFields gates keeping the original rule verbatim. It must be
    // true when the form still holds the values the loader seeded from the rule,
    // even when the rule carries tokens the editor cannot model.
    @Test fun matches_editor_fields_true_when_untouched_including_unmodeled_tokens() {
        // a sub-day UNTIL time and a BYDAY the editor never surfaces: the form
        // shows freq=Weekly, interval=1, untilDate=2026-03-01, count=null.
        assertEquals(
            true,
            RecurrenceRule.matchesEditorFields(
                "FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20260301T080000Z",
                RecurrenceFrequency.Weekly,
                interval = 1,
                untilDate = LocalDate.of(2026, 3, 1),
                count = null,
            ),
        )
    }

    @Test fun matches_editor_fields_false_when_a_field_changed() {
        // user moved the end date a day later: must rebuild, not keep the rule.
        assertEquals(
            false,
            RecurrenceRule.matchesEditorFields(
                "FREQ=WEEKLY;UNTIL=20260301T080000Z",
                RecurrenceFrequency.Weekly,
                interval = 1,
                untilDate = LocalDate.of(2026, 3, 2),
                count = null,
            ),
        )
    }

    // RFC 5545 §3.3.10 forbids UNTIL and COUNT in the same rule, but imported
    // ICS / CalDAV rows can carry both. Prefer UNTIL and drop COUNT rather than
    // throwing, so saving such an event can't crash the editor.
    @Test fun prefers_until_when_both_until_and_count_present() {
        val r =
            RecurrenceRule.build(
                frequency = RecurrenceFrequency.Weekly,
                interval = 1,
                untilDate = LocalDate.of(2026, 12, 31),
                count = 10,
            )
        assertEquals("FREQ=WEEKLY;UNTIL=20261231T235959Z", r)
    }

    // F6: a timed UNTIL closes the chosen day at the EVENT zone's end-of-day,
    // expressed in UTC. A blanket T235959Z stamps a UTC time on a local date and
    // drops a boundary-day occurrence in zones offset from UTC.
    @Test fun builds_timed_until_at_event_zone_end_of_day_in_utc() {
        val r =
            RecurrenceRule.build(
                frequency = RecurrenceFrequency.Daily,
                interval = 1,
                untilDate = LocalDate.of(2026, 12, 31),
                count = null,
                allDay = false,
                zoneId = ZoneId.of("America/New_York"),
            )
        // 2026-12-31 23:59:59 EST (UTC-5) = 2027-01-01 04:59:59 UTC.
        assertEquals("FREQ=DAILY;UNTIL=20270101T045959Z", r)
    }

    // the UTC datetime round-trips back to the local end-date the user picked
    // when read in the same event zone.
    @Test fun until_date_round_trips_through_event_zone() {
        assertEquals(
            LocalDate.of(2026, 12, 31),
            RecurrenceRule.untilDateOf("FREQ=DAILY;UNTIL=20270101T045959Z", ZoneId.of("America/New_York")),
        )
    }

    // matchesEditorFields uses the event zone, so an unchanged non-UTC series is
    // kept verbatim (not rebuilt) when the editor reseeds the same local date.
    @Test fun matches_editor_fields_true_for_unchanged_non_utc_series() {
        assertEquals(
            true,
            RecurrenceRule.matchesEditorFields(
                "FREQ=DAILY;UNTIL=20270101T045959Z",
                RecurrenceFrequency.Daily,
                interval = 1,
                untilDate = LocalDate.of(2026, 12, 31),
                count = null,
                zoneId = ZoneId.of("America/New_York"),
            ),
        )
    }
}
