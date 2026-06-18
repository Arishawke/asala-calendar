/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.data

import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventDraftTest {
    @Test fun timed_event_writes_dtstart_dtend_when_no_rrule() {
        val draft =
            EventDraft(
                calendarId = 1L,
                title = "Lunch",
                description = "with M",
                location = "Cafe",
                startMillis = 1_700_000_000_000L,
                endMillis = 1_700_003_600_000L,
                allDay = false,
                eventTimezone = "America/New_York",
                rrule = null,
            )

        val m = draft.toMap()

        assertEquals(1L, m[CalendarContract.Events.CALENDAR_ID])
        assertEquals("Lunch", m[CalendarContract.Events.TITLE])
        assertEquals("with M", m[CalendarContract.Events.DESCRIPTION])
        assertEquals("Cafe", m[CalendarContract.Events.EVENT_LOCATION])
        assertEquals(1_700_000_000_000L, m[CalendarContract.Events.DTSTART])
        assertEquals(1_700_003_600_000L, m[CalendarContract.Events.DTEND])
        assertEquals(0, m[CalendarContract.Events.ALL_DAY])
        assertEquals("America/New_York", m[CalendarContract.Events.EVENT_TIMEZONE])
        assertNull(m[CalendarContract.Events.RRULE])
        assertNull(m[CalendarContract.Events.DURATION])
    }

    @Test fun recurring_event_writes_duration_not_dtend() {
        val draft =
            EventDraft(
                calendarId = 1L,
                title = "Standup",
                description = null,
                location = null,
                startMillis = 1_700_000_000_000L,
                endMillis = 1_700_001_800_000L, // 30 minutes
                allDay = false,
                eventTimezone = "America/New_York",
                rrule = "FREQ=DAILY",
            )

        val m = draft.toMap()

        assertEquals("FREQ=DAILY", m[CalendarContract.Events.RRULE])
        assertEquals("P0DT0H30M0S", m[CalendarContract.Events.DURATION])
        assertNull(m[CalendarContract.Events.DTEND])
    }

    @Test fun recurring_event_preserves_seconds_remainder_in_duration() {
        // recurring rows store DURATION; the seconds remainder must survive so a
        // sub-minute occurrence length is not silently rounded down to the minute.
        val draft =
            EventDraft(
                calendarId = 1L,
                title = "Standup",
                description = null,
                location = null,
                startMillis = 1_700_000_000_000L,
                endMillis = 1_700_000_000_000L + (30 * 60 + 45) * 1000L, // 30m45s
                allDay = false,
                eventTimezone = "America/New_York",
                rrule = "FREQ=DAILY",
            )

        assertEquals("P0DT0H30M45S", draft.toMap()[CalendarContract.Events.DURATION])
    }

    @Test fun all_day_recurring_event_writes_day_form_duration() {
        // The provider's fixAllDayTime treats an all-day duration ending in 'S'
        // as the pure-seconds form and does Integer.parseInt on the P..S body;
        // a time-component form like P1DT0H0M0S NumberFormat-crashes the insert.
        // Day form sidesteps it (and is the RFC 5545 form for DATE values).
        val draft =
            EventDraft(
                calendarId = 1L,
                title = "Vacation",
                description = null,
                location = null,
                startMillis = 1_700_000_000_000L,
                endMillis = 1_700_086_400_000L, // +1 day
                allDay = true,
                eventTimezone = "UTC",
                rrule = "FREQ=DAILY",
            )

        val m = draft.toMap()

        assertEquals("FREQ=DAILY", m[CalendarContract.Events.RRULE])
        assertEquals("P1D", m[CalendarContract.Events.DURATION])
        assertNull(m[CalendarContract.Events.DTEND])
    }

    @Test fun all_day_multi_day_recurring_uses_day_count_duration() {
        // a 3-day span must carry P3D, not collapse to a single day.
        val draft =
            EventDraft(
                calendarId = 1L,
                title = "Conference",
                description = null,
                location = null,
                startMillis = 1_700_000_000_000L,
                endMillis = 1_700_000_000_000L + 3L * 86_400_000L,
                allDay = true,
                eventTimezone = "UTC",
                rrule = "FREQ=WEEKLY",
            )

        assertEquals("P3D", draft.toMap()[CalendarContract.Events.DURATION])
    }

    @Test fun parses_own_duration_round_trip() {
        // 30 min, the form we emit
        assertEquals(30 * 60 * 1000L, EventDraft.parseIso8601DurationMs("P0DT0H30M0S"))
        // 1 hour
        assertEquals(60 * 60 * 1000L, EventDraft.parseIso8601DurationMs("P0DT1H0M0S"))
        // 1 day, the shorter RFC 5545 form emitted for all-day recurring
        assertEquals(24L * 60 * 60 * 1000L, EventDraft.parseIso8601DurationMs("P1D"))
        // hours+minutes only
        assertEquals(90L * 60 * 1000L, EventDraft.parseIso8601DurationMs("PT1H30M"))
        // multi-day
        assertEquals((2 * 86400L + 3 * 3600L) * 1000L, EventDraft.parseIso8601DurationMs("P2DT3H0M0S"))
        // weeks (RFC 5545 form, accepted defensively)
        assertEquals(7L * 86400 * 1000, EventDraft.parseIso8601DurationMs("P1W"))
    }

    @Test fun parses_seconds_only_form_without_t() {
        // The post-sync form some sync adapters write back into the row
        // after the event makes a server round-trip. Not strict RFC 5545.
        assertEquals(3600 * 1000L, EventDraft.parseIso8601DurationMs("P3600S"))
        assertEquals(1800 * 1000L, EventDraft.parseIso8601DurationMs("P1800S"))
    }

    @Test fun parse_duration_returns_null_for_malformed() {
        assertNull(EventDraft.parseIso8601DurationMs(null))
        assertNull(EventDraft.parseIso8601DurationMs(""))
        assertNull(EventDraft.parseIso8601DurationMs("P"))
        assertNull(EventDraft.parseIso8601DurationMs("garbage"))
        assertNull(EventDraft.parseIso8601DurationMs("PT")) // matches regex but yields zero in all groups
    }

    @Test fun null_status_and_availability_fall_back_to_confirmed_and_busy() {
        val draft =
            EventDraft(
                calendarId = 1L,
                title = "Lunch",
                description = null,
                location = null,
                startMillis = 1_700_000_000_000L,
                endMillis = 1_700_003_600_000L,
                allDay = false,
                eventTimezone = "America/New_York",
                rrule = null,
                status = null,
                availability = null,
            )

        val m = draft.toMap()

        assertEquals(CalendarContract.Events.STATUS_CONFIRMED, m[CalendarContract.Events.STATUS])
        assertEquals(CalendarContract.Events.AVAILABILITY_BUSY, m[CalendarContract.Events.AVAILABILITY])
    }

    @Test fun loaded_status_and_availability_pass_through_on_edit() {
        // Update path: caller passes the values loaded from the existing
        // EventDetail so a Tentative / Free event the user set server-side
        // is not clobbered back to CONFIRMED / BUSY by an unrelated edit.
        val draft =
            EventDraft(
                calendarId = 1L,
                title = "Standup",
                description = null,
                location = null,
                startMillis = 1_700_000_000_000L,
                endMillis = 1_700_003_600_000L,
                allDay = false,
                eventTimezone = "America/New_York",
                rrule = null,
                status = CalendarContract.Events.STATUS_TENTATIVE,
                availability = CalendarContract.Events.AVAILABILITY_FREE,
            )

        val m = draft.toMap()

        assertEquals(CalendarContract.Events.STATUS_TENTATIVE, m[CalendarContract.Events.STATUS])
        assertEquals(CalendarContract.Events.AVAILABILITY_FREE, m[CalendarContract.Events.AVAILABILITY])
    }

    @Test fun all_day_event_uses_utc_flag() {
        val draft =
            EventDraft(
                calendarId = 1L,
                title = "Vacation",
                description = null,
                location = null,
                startMillis = 1_700_000_000_000L,
                endMillis = 1_700_086_400_000L,
                allDay = true,
                eventTimezone = "UTC",
                rrule = null,
            )

        val m = draft.toMap()

        assertEquals(1, m[CalendarContract.Events.ALL_DAY])
        assertEquals("UTC", m[CalendarContract.Events.EVENT_TIMEZONE])
    }
}
