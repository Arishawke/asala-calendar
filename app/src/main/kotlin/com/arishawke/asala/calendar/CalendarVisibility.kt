/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar

import com.arishawke.asala.calendar.data.CalendarItem
import com.arishawke.asala.calendar.data.StorageMode
import com.arishawke.asala.calendar.data.StorageModeFilter

// single source of truth for "which calendars are hidden": the union of the
// explicit per-calendar drawer hides, every calendar under a drawer-hidden
// account, and the storage-mode hides. shared by AppViewModel (reactive) and
// WidgetEventSource (one-shot) so the in-app views and widgets can't drift.
internal fun computeHiddenCalendarIds(
    hiddenCalendarIds: Set<Long>,
    drawerHiddenAccountKeys: Set<String>,
    storageMode: StorageMode,
    calendars: List<CalendarItem>,
): Set<Long> {
    val accountHidden = calendars
        .filter { drawerAccountKey(it.accountType, it.accountName) in drawerHiddenAccountKeys }
        .mapTo(mutableSetOf()) { it.id }
    return hiddenCalendarIds + accountHidden + StorageModeFilter.modeHiddenIds(storageMode, calendars)
}
