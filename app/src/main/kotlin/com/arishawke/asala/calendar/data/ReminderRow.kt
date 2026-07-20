/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.data

// a single CalendarContract.Reminders row: the offset plus its delivery method.
// method is load-bearing: METHOD_EMAIL/SMS/ALARM are server-owned, stored locally
// only to round-trip back to the sync adapter, so rewriting one to METHOD_ALERT
// corrupts a synced calendar's reminder. carried so a save preserves foreign rows.
data class ReminderRow(val minutes: Int, val method: Int)
