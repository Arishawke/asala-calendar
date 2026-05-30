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
import androidx.compose.runtime.Immutable

// One account's selectable calendars, for the event editor's account +
// chip-row selector. Grouping is presentation only; selectability is still
// decided by `filter`. @Immutable so it passes as a stable Compose param.
@Immutable
data class CalendarAccountGroup(val accountName: String, val calendars: List<CalendarItem>)

// Picker contract for the event editor's calendar dropdown: only writable
// rows, narrowed by storage mode so the user cannot create an event on a
// calendar the chosen mode is meant to hide, and finally narrowed by the
// effective hide set (manual drawer-toggles plus account-level hides
// from `drawerHiddenAccountKeys`) so calendars the user has chosen to
// hide from the drawer don't reappear as create targets here.
object EventEditCalendarPicker {
    fun filter(
        calendars: List<CalendarItem>,
        mode: StorageMode,
        hiddenCalendarIds: Set<Long> = emptySet(),
    ): List<CalendarItem> = calendars
        .filter { it.visible && it.isWritable }
        .filter { it.id !in hiddenCalendarIds }
        .filter { c ->
            when (mode) {
                StorageMode.LocalOnly ->
                    c.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL
                StorageMode.SyncOnly ->
                    c.accountType != CalendarContract.ACCOUNT_TYPE_LOCAL
                StorageMode.Hybrid, StorageMode.Unset -> true
            }
        }

    // Groups already-filtered calendars by account for the chip-row selector.
    // groupBy preserves first-seen account order and each account's input
    // order, so the repo's ordering carries through to the UI unchanged.
    fun groupByAccount(calendars: List<CalendarItem>): List<CalendarAccountGroup> = calendars
        .groupBy { it.accountName }
        .map { (account, cals) -> CalendarAccountGroup(account, cals) }
}
