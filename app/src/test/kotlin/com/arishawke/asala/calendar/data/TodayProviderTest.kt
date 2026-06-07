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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TodayProviderTest {
    @Test fun initial_today_comes_from_clock() {
        val fixed = LocalDate.of(2026, 5, 27)
        val clock = Clock.fixed(fixed.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

        val provider = TodayProvider(clock, zoneId = { ZoneOffset.UTC })

        assertEquals(fixed, provider.today.value)
    }

    @Test fun refresh_advances_the_flow_when_clock_crosses_midnight() {
        val movingClock = MutableClock(
            LocalDate.of(2026, 5, 27).atStartOfDay(ZoneOffset.UTC).toInstant(),
        )
        val provider = TodayProvider(movingClock, zoneId = { ZoneOffset.UTC })
        assertEquals(LocalDate.of(2026, 5, 27), provider.today.value)

        movingClock.set(LocalDate.of(2026, 5, 28).atStartOfDay(ZoneOffset.UTC).toInstant())
        provider.refresh()

        assertEquals(LocalDate.of(2026, 5, 28), provider.today.value)
    }

    @Test fun refresh_is_idempotent_within_the_same_day() {
        val fixed = LocalDate.of(2026, 5, 27)
        val clock = Clock.fixed(fixed.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
        val provider = TodayProvider(clock, zoneId = { ZoneOffset.UTC })
        val before = provider.today.value

        provider.refresh()

        assertEquals(before, provider.today.value)
    }

    // refresh() must re-read the zone, not the one frozen at construction:
    // a tz change near local midnight moves the calendar date for a fixed
    // instant. 23:30 UTC is already the 28th in Auckland (UTC+12) but the
    // 27th in Pago Pago (UTC-11).
    @Test fun refresh_picks_up_a_timezone_change_that_moves_the_local_date() {
        val instant = LocalDate.of(2026, 5, 27).atTime(23, 30).atZone(ZoneOffset.UTC).toInstant()
        val clock = Clock.fixed(instant, ZoneOffset.UTC)
        var zone: ZoneId = ZoneId.of("Pacific/Auckland")
        val provider = TodayProvider(clock, zoneId = { zone })
        assertEquals(LocalDate.of(2026, 5, 28), provider.today.value)

        zone = ZoneId.of("Pacific/Pago_Pago")
        provider.refresh()

        assertEquals(LocalDate.of(2026, 5, 27), provider.today.value)
    }
}

private class MutableClock(initial: Instant) : Clock() {
    @Volatile
    private var instant: Instant = initial

    fun set(newInstant: Instant) {
        instant = newInstant
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = error("not needed")

    override fun instant(): Instant = instant
}
