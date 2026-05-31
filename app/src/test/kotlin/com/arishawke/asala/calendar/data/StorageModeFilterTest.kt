/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageModeFilterTest {
    private fun cal(id: Long, accountType: String) = CalendarItem(
        id = id,
        displayName = "cal$id",
        accountName = "acc$id",
        accountType = accountType,
        color = 0,
        visible = true,
        accessLevel = 700,
    )

    private val local1 = cal(1L, CalendarContract.ACCOUNT_TYPE_LOCAL)
    private val local2 = cal(2L, CalendarContract.ACCOUNT_TYPE_LOCAL)
    private val sync1 = cal(10L, "com.google")
    private val sync2 = cal(11L, "com.exchange")

    // Local only must hide every non-local calendar; local rows stay
    // visible. This is the contract that lets users land on a clean local
    // ledger after onboarding.
    @Test
    fun `LocalOnly returns every non-local id`() {
        val hidden =
            StorageModeFilter.modeHiddenIds(
                mode = StorageMode.LocalOnly,
                calendars = listOf(local1, sync1, local2, sync2),
            )
        assertEquals(setOf(sync1.id, sync2.id), hidden)
    }

    // No sync calendars present is the trivial fresh-install case. Without
    // this, a returning Local only user would see an empty drawer flicker
    // before observeCalendars settles.
    @Test
    fun `LocalOnly with no sync calendars returns empty`() {
        val hidden =
            StorageModeFilter.modeHiddenIds(
                mode = StorageMode.LocalOnly,
                calendars = listOf(local1, local2),
            )
        assertEquals(emptySet<Long>(), hidden)
    }

    // Sync only is the mirror of Local only: it hides every on-device
    // (local) calendar so only synced calendars render, matching the drawer
    // and the event-editor picker. A non-empty mode set is safe because
    // toggleCalendarVisibility writes the user-only set, never the mode
    // union, so these derived hides never stomp manual drawer toggles.
    @Test
    fun `SyncOnly returns every local id`() {
        val hidden =
            StorageModeFilter.modeHiddenIds(
                mode = StorageMode.SyncOnly,
                calendars = listOf(local1, sync1, local2, sync2),
            )
        assertEquals(setOf(local1.id, local2.id), hidden)
    }

    // No local calendars present: nothing for Sync only to hide.
    @Test
    fun `SyncOnly with no local calendars returns empty`() {
        val hidden =
            StorageModeFilter.modeHiddenIds(
                mode = StorageMode.SyncOnly,
                calendars = listOf(sync1, sync2),
            )
        assertEquals(emptySet<Long>(), hidden)
    }

    // Hybrid mirrors Sync only's contract: the mode itself never hides
    // anything; the user owns the hide set via the drawer.
    @Test
    fun `Hybrid returns empty`() {
        val hidden =
            StorageModeFilter.modeHiddenIds(
                mode = StorageMode.Hybrid,
                calendars = listOf(local1, sync1),
            )
        assertEquals(emptySet<Long>(), hidden)
    }

    // Unset is the pre-onboarding state. We must not project hides yet or
    // the empty initial composition would suppress sync calendars before
    // the user has chosen a mode.
    @Test
    fun `Unset returns empty`() {
        val hidden =
            StorageModeFilter.modeHiddenIds(
                mode = StorageMode.Unset,
                calendars = listOf(local1, sync1),
            )
        assertEquals(emptySet<Long>(), hidden)
    }
}
