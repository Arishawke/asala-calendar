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

class EventEditCalendarPickerTest {
    private fun cal(
        id: Long,
        accountType: String,
        visible: Boolean = true,
        accessLevel: Int = 700, // CAL_ACCESS_OWNER
    ) = CalendarItem(
        id = id,
        displayName = "cal$id",
        accountName = "acc$id",
        accountType = accountType,
        color = 0,
        visible = visible,
        accessLevel = accessLevel,
    )

    private val localWritable = cal(1L, CalendarContract.ACCOUNT_TYPE_LOCAL)
    private val syncWritable = cal(2L, "com.google")
    private val syncReadOnly = cal(3L, "com.holidays", accessLevel = 200)
    private val syncHidden = cal(4L, "com.exchange", visible = false)

    // Read-only and hidden rows must be excluded under every mode so the
    // user cannot pick a calendar that will silently reject the insert.
    @Test
    fun `read-only and hidden rows are excluded in every mode`() {
        listOf(StorageMode.LocalOnly, StorageMode.SyncOnly, StorageMode.Hybrid, StorageMode.Unset)
            .forEach { mode ->
                val result =
                    EventEditCalendarPicker.filter(
                        calendars = listOf(localWritable, syncWritable, syncReadOnly, syncHidden),
                        mode = mode,
                    )
                assertEquals(
                    "mode=$mode should drop the read-only and hidden rows",
                    false,
                    result.any { it.id == syncReadOnly.id || it.id == syncHidden.id },
                )
            }
    }

    // Local only must surface only the Asala local calendar; a sync row
    // selected here would let the user create an event the mode is meant
    // to hide.
    @Test
    fun `LocalOnly keeps only local writable rows`() {
        val result =
            EventEditCalendarPicker.filter(
                calendars = listOf(localWritable, syncWritable, syncReadOnly),
                mode = StorageMode.LocalOnly,
            )
        assertEquals(listOf(localWritable), result)
    }

    // Sync only is the symmetric case: hide the local Asala calendar so
    // the picker can never default to it.
    @Test
    fun `SyncOnly keeps only sync writable rows`() {
        val result =
            EventEditCalendarPicker.filter(
                calendars = listOf(localWritable, syncWritable),
                mode = StorageMode.SyncOnly,
            )
        assertEquals(listOf(syncWritable), result)
    }

    // Hybrid keeps every writable visible row, regardless of account type.
    @Test
    fun `Hybrid keeps every writable visible row`() {
        val result =
            EventEditCalendarPicker.filter(
                calendars = listOf(localWritable, syncWritable, syncReadOnly, syncHidden),
                mode = StorageMode.Hybrid,
            )
        assertEquals(listOf(localWritable, syncWritable), result)
    }

    // Unset is the pre-onboarding state and should behave like Hybrid for
    // the picker so the user never sees an empty dropdown if they manage
    // to open the editor before completing the storage-mode picker.
    @Test
    fun `Unset behaves like Hybrid`() {
        val result =
            EventEditCalendarPicker.filter(
                calendars = listOf(localWritable, syncWritable),
                mode = StorageMode.Unset,
            )
        assertEquals(listOf(localWritable, syncWritable), result)
    }

    // hiddenCalendarIds (the drawer's effective hide set, combining the
    // user's manual toggles and account-level hides) must drop matching
    // rows so the editor can't surface a calendar that's hidden from the
    // drawer.
    @Test
    fun `hiddenCalendarIds drops matching rows under Hybrid`() {
        val result =
            EventEditCalendarPicker.filter(
                calendars = listOf(localWritable, syncWritable),
                mode = StorageMode.Hybrid,
                hiddenCalendarIds = setOf(syncWritable.id),
            )
        assertEquals(listOf(localWritable), result)
    }

    @Test
    fun `hiddenCalendarIds applies alongside storage mode filter`() {
        val result =
            EventEditCalendarPicker.filter(
                calendars = listOf(localWritable, syncWritable),
                mode = StorageMode.SyncOnly,
                hiddenCalendarIds = setOf(syncWritable.id),
            )
        // SyncOnly already drops local; hiding the only sync row leaves
        // an empty picker. Editor surfaces this to the user; the filter's
        // job is to not silently surface a hidden row.
        assertEquals(emptyList<CalendarItem>(), result)
    }

    private fun groupCal(id: Long, account: String) = CalendarItem(
        id = id,
        displayName = "cal$id",
        accountName = account,
        accountType = "com.google",
        color = 0,
        visible = true,
        accessLevel = 700,
    )

    @Test
    fun `groupByAccount returns empty for empty input`() {
        assertEquals(emptyList<CalendarAccountGroup>(), EventEditCalendarPicker.groupByAccount(emptyList()))
    }

    // One account collapses to a single group; the chip row must render
    // calendars in the order the repo supplied them (no reshuffle).
    @Test
    fun `groupByAccount keeps one account in a single ordered group`() {
        val cals = listOf(groupCal(1L, "me@x.com"), groupCal(2L, "me@x.com"), groupCal(3L, "me@x.com"))
        val groups = EventEditCalendarPicker.groupByAccount(cals)
        assertEquals(1, groups.size)
        assertEquals("me@x.com", groups[0].accountName)
        assertEquals(listOf(1L, 2L, 3L), groups[0].calendars.map { it.id })
    }

    // Two accounts produce two groups in first-seen order, each preserving
    // its calendars' input order even when the input interleaves accounts.
    // First-seen order (zeta, alpha) is deliberately the reverse of
    // alphabetical, so the test fails if grouping ever sorts instead.
    @Test
    fun `groupByAccount splits accounts in first-seen order`() {
        val cals = listOf(
            groupCal(1L, "zeta@x.com"),
            groupCal(2L, "alpha@y.com"),
            groupCal(3L, "zeta@x.com"),
            groupCal(4L, "alpha@y.com"),
        )
        val groups = EventEditCalendarPicker.groupByAccount(cals)
        assertEquals(listOf("zeta@x.com", "alpha@y.com"), groups.map { it.accountName })
        assertEquals(listOf(1L, 3L), groups[0].calendars.map { it.id })
        assertEquals(listOf(2L, 4L), groups[1].calendars.map { it.id })
    }
}
