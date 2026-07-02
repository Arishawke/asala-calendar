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

    // a snooze keeps its own slot, independent of the reminder offset, so a
    // routine rescheduleAll cancelling the original forAlarm slot cannot drop a
    // pending snooze, and the "0 minutes before" reminder cannot collide with it.
    fun forSnoozeAlarm(eventId: Long, instanceMillis: Long): Int = "snoozeAlarm:$eventId:$instanceMillis".hashCode()

    fun forOpen(eventId: Long, instanceMillis: Long): Int = "open:$eventId:$instanceMillis".hashCode()

    fun forSnoozeDefault(eventId: Long, instanceMillis: Long, minutesBefore: Int): Int =
        "snoozeDefault:$eventId:$instanceMillis:$minutesBefore".hashCode()

    fun forSnoozePicker(eventId: Long, instanceMillis: Long, minutesBefore: Int): Int =
        "snoozePicker:$eventId:$instanceMillis:$minutesBefore".hashCode()

    fun forDismiss(eventId: Long, instanceMillis: Long, minutesBefore: Int): Int =
        "dismiss:$eventId:$instanceMillis:$minutesBefore".hashCode()

    // per-instance AND per-offset so two reminders firing for one occurrence get
    // distinct shade entries instead of the later one replacing the earlier.
    // forOpen omits the offset: its extras are identical across a fire's offsets,
    // so a shared PendingIntent opens the same event either way.
    fun forNotification(eventId: Long, instanceMillis: Long, minutesBefore: Int): Int =
        "notif:$eventId:$instanceMillis:$minutesBefore".hashCode()
}
