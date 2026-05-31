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

// one account's selectable calendars for the editor's chip-row selector.
// grouping is presentation only; selectability is decided by `filter`.
@Immutable
data class CalendarAccountGroup(val accountName: String, val calendars: List<CalendarItem>)

// editor calendar dropdown: writable rows only, narrowed by storage mode and
// by the hide set so drawer-hidden calendars don't reappear as create targets.
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

    // groupBy preserves first-seen account order and input order, so the
    // repo's ordering carries through to the UI unchanged.
    fun groupByAccount(calendars: List<CalendarItem>): List<CalendarAccountGroup> = calendars
        .groupBy { it.accountName }
        .map { (account, cals) -> CalendarAccountGroup(account, cals) }
}
