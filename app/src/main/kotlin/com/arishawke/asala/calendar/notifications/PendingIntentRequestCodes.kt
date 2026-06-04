/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.notifications

// hash the namespace plus the 64-bit ids: Long.toInt() drops the upper 32 bits,
// so instances ~49.7 days apart collide under FLAG_UPDATE_CURRENT.
internal object PendingIntentRequestCodes {
    // string format bit-matches the original requestCodeFor so alarms from older
    // builds survive an in-place upgrade without orphaning their PendingIntents.
    fun forAlarm(eventId: Long, instanceMillis: Long, minutesBefore: Int): Int =
        "$eventId:$instanceMillis:$minutesBefore".hashCode()

    fun forOpen(eventId: Long, instanceMillis: Long): Int = "open:$eventId:$instanceMillis".hashCode()

    fun forSnoozeDefault(eventId: Long, instanceMillis: Long): Int = "snoozeDefault:$eventId:$instanceMillis".hashCode()

    fun forSnoozePicker(eventId: Long, instanceMillis: Long): Int = "snoozePicker:$eventId:$instanceMillis".hashCode()

    fun forDismiss(eventId: Long, instanceMillis: Long): Int = "dismiss:$eventId:$instanceMillis".hashCode()

    // per-instance so two pending reminders of one recurring event get distinct
    // shade entries instead of the later one replacing the earlier.
    fun forNotification(eventId: Long, instanceMillis: Long): Int = "notif:$eventId:$instanceMillis".hashCode()
}
