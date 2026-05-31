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

// Mode-driven hides are derived on read, never persisted. Keeping them
// computed (rather than baked into the user's hidden-ids preference) is
// what stops a Local only -> Hybrid -> Local only round trip from
// stomping the user's manual drawer toggles.
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
}
