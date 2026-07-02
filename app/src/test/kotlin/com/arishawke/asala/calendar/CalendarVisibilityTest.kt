/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar

import android.provider.CalendarContract
import com.arishawke.asala.calendar.data.CalendarItem
import com.arishawke.asala.calendar.data.StorageMode
import org.junit.Assert.assertEquals
import org.junit.Test

// pins the single visibility rule shared by AppViewModel and the widgets, so
// the in-app views and the home-screen widgets can never drift on which
// calendars are hidden.
class CalendarVisibilityTest {
    private fun cal(id: Long, accountType: String, accountName: String) = CalendarItem(
        id = id,
        displayName = "cal$id",
        accountName = accountName,
        accountType = accountType,
        color = 0,
        visible = true,
        accessLevel = 700,
    )

    private val local = cal(1L, CalendarContract.ACCOUNT_TYPE_LOCAL, "local")
    private val googleA1 = cal(10L, "com.google", "a@gmail.com")
    private val googleA2 = cal(11L, "com.google", "a@gmail.com")
    private val googleB = cal(12L, "com.google", "b@gmail.com")
    private val all = listOf(local, googleA1, googleA2, googleB)

    // the three hide sources must union, never replace: a calendar hidden by
    // any one source stays hidden regardless of the others.
    @Test
    fun `unions explicit, account, and storage-mode hides`() {
        val hidden = computeHiddenCalendarIds(
            hiddenCalendarIds = setOf(googleB.id),
            drawerHiddenAccountKeys = setOf("com.google:a@gmail.com"),
            storageMode = StorageMode.SyncOnly,
            calendars = all,
            occasionCalendarIds = emptySet(),
        )
        // explicit: googleB. account a hidden: googleA1 + googleA2. SyncOnly hides local.
        assertEquals(setOf(googleB.id, googleA1.id, googleA2.id, local.id), hidden)
    }

    // a drawer-hidden account hides every calendar under it, not just one.
    @Test
    fun `hides every calendar under a drawer-hidden account`() {
        val hidden = computeHiddenCalendarIds(
            hiddenCalendarIds = emptySet(),
            drawerHiddenAccountKeys = setOf("com.google:a@gmail.com"),
            storageMode = StorageMode.Hybrid,
            calendars = all,
            occasionCalendarIds = emptySet(),
        )
        assertEquals(setOf(googleA1.id, googleA2.id), hidden)
    }

    // explicit per-calendar hides pass through untouched.
    @Test
    fun `passes through explicit hidden ids`() {
        val hidden = computeHiddenCalendarIds(
            hiddenCalendarIds = setOf(googleB.id, googleA1.id),
            drawerHiddenAccountKeys = emptySet(),
            storageMode = StorageMode.Hybrid,
            calendars = all,
            occasionCalendarIds = emptySet(),
        )
        assertEquals(setOf(googleB.id, googleA1.id), hidden)
    }

    // Hybrid never mode-hides, so with no explicit or account hides nothing is hidden.
    @Test
    fun `hides nothing when no source applies`() {
        val hidden = computeHiddenCalendarIds(
            hiddenCalendarIds = emptySet(),
            drawerHiddenAccountKeys = emptySet(),
            storageMode = StorageMode.Hybrid,
            calendars = all,
            occasionCalendarIds = emptySet(),
        )
        assertEquals(emptySet<Long>(), hidden)
    }

    // the provisioned occasion calendars are feature-owned, not user "local
    // storage": SyncOnly must not mode-hide them, or contact birthdays and
    // anniversaries silently never render for sync-only users.
    @Test
    fun `SyncOnly keeps provisioned occasion calendars visible`() {
        val otherLocal = cal(2L, CalendarContract.ACCOUNT_TYPE_LOCAL, "local")
        val hidden = computeHiddenCalendarIds(
            hiddenCalendarIds = emptySet(),
            drawerHiddenAccountKeys = emptySet(),
            storageMode = StorageMode.SyncOnly,
            calendars = all + otherLocal,
            occasionCalendarIds = setOf(local.id),
        )
        // the occasion calendar (1) survives the mode hide; other local (2) does not.
        assertEquals(setOf(otherLocal.id), hidden)
    }

    // a manual drawer hide on an occasion calendar still wins: the SyncOnly
    // exemption only lifts the mode-derived hide, never a user choice.
    @Test
    fun `explicit hide on an occasion calendar still applies`() {
        val hidden = computeHiddenCalendarIds(
            hiddenCalendarIds = setOf(local.id),
            drawerHiddenAccountKeys = emptySet(),
            storageMode = StorageMode.SyncOnly,
            calendars = all,
            occasionCalendarIds = setOf(local.id),
        )
        assertEquals(setOf(local.id), hidden)
    }
}
