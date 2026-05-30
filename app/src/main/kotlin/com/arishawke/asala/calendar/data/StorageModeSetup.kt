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

// One side-effect helper shared by the first-run permission gate and the
// Settings > Storage switch. Idempotent at the user level: if the Asala
// local calendar already exists for our account, no row is created.
object StorageModeSetup {
    suspend fun ensureLocalCalendarIfNeeded(repo: CalendarRepository, mode: StorageMode) {
        if (mode != StorageMode.LocalOnly && mode != StorageMode.Hybrid) return
        val exists =
            repo.calendars().any {
                it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL &&
                    it.accountName == LocalCalendar.AccountName
            }
        if (!exists) {
            repo.createLocalCalendar(LocalCalendar.DisplayName, LocalCalendar.DefaultColor)
        }
    }
}
