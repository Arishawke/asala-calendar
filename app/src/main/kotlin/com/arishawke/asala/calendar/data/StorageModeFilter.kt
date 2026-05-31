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

// mode-driven hides are derived on read, never persisted, so a
// LocalOnly -> Hybrid -> LocalOnly round trip can't stomp manual drawer toggles.
object StorageModeFilter {
    fun modeHiddenIds(mode: StorageMode, calendars: List<CalendarItem>): Set<Long> = when (mode) {
        StorageMode.LocalOnly ->
            calendars
                .filter { it.accountType != CalendarContract.ACCOUNT_TYPE_LOCAL }
                .mapTo(mutableSetOf()) { it.id }
        StorageMode.SyncOnly ->
            calendars
                .filter { it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL }
                .mapTo(mutableSetOf()) { it.id }
        StorageMode.Hybrid,
        StorageMode.Unset,
        -> emptySet()
    }

    // full account hiding (drawer + Settings restore list), not just events:
    // LocalOnly hides synced accounts; SyncOnly hides the on-device account.
    fun accountHiddenByMode(accountType: String, mode: StorageMode): Boolean = when (mode) {
        StorageMode.LocalOnly -> accountType != CalendarContract.ACCOUNT_TYPE_LOCAL
        StorageMode.SyncOnly -> accountType == CalendarContract.ACCOUNT_TYPE_LOCAL
        StorageMode.Hybrid, StorageMode.Unset -> false
    }
}
