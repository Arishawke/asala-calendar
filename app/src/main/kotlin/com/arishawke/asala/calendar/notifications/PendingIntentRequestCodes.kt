/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

// Stable PendingIntent request codes for the reminder flow. Hashing a string
// that includes the action namespace plus all relevant 64-bit ids avoids the
// silent collision that comes from Long.toInt() (which discards the upper 32
// bits and lets two distinct event instances ~49.7 days apart overwrite each
// other under FLAG_UPDATE_CURRENT).
internal object PendingIntentRequestCodes {
    // Format chosen to bit-match the original ReminderScheduler.requestCodeFor so
    // alarms scheduled by older builds survive an in-place upgrade without
    // orphaning their PendingIntents in AlarmManager.
    fun forAlarm(eventId: Long, instanceMillis: Long, minutesBefore: Int): Int =
        "$eventId:$instanceMillis:$minutesBefore".hashCode()

    fun forOpen(eventId: Long, instanceMillis: Long): Int = "open:$eventId:$instanceMillis".hashCode()

    fun forSnoozeDefault(eventId: Long, instanceMillis: Long): Int = "snoozeDefault:$eventId:$instanceMillis".hashCode()

    fun forSnoozePicker(eventId: Long, instanceMillis: Long): Int = "snoozePicker:$eventId:$instanceMillis".hashCode()

    fun forDismiss(eventId: Long, instanceMillis: Long): Int = "dismiss:$eventId:$instanceMillis".hashCode()
}
